/*******************************************************************************
 * This file is part of RedReader.
 *
 * RedReader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RedReader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with RedReader.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package org.quantumbadger.redreader.eink;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.util.LruCache;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.quantumbadger.redreader.R;
import org.quantumbadger.redreader.common.GenericFactory;
import org.quantumbadger.redreader.common.datastream.SeekableInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 复用原下载缓存，只解码当前图片区域，并以原比例顶部对齐显示。 */
public final class EinkImageView extends LinearLayout {
	private final ExecutorService mWorker = Executors.newSingleThreadExecutor();
	private final Handler mMain = new Handler(Looper.getMainLooper());
	private final ImageView mImage;
	private final TextView mStatus;
	private final Button mPrevious;
	private final Button mNext;
	private final Button mMode;
	private final LruCache<String, Bitmap> mCache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
		@Override
		protected int sizeOf(final String key, final Bitmap value) {
			return value.getAllocationByteCount();
		}
	};
	private BitmapRegionDecoder mDecoder;
	private Bitmap mPreview;
	private File mFile;
	private volatile boolean mDisposed;
	private volatile int mGeneration;
	private int mWidth;
	private int mHeight;
	private int mIndex;
	private boolean mOriginal;
	private ImagePages mPages;

	public EinkImageView(final Context context,
			final GenericFactory<SeekableInputStream, IOException> factory,
			final Runnable previousImage, final Runnable nextImage,
			final View.OnClickListener actions) {
		super(context);
		setOrientation(VERTICAL);
		setBackgroundColor(Color.WHITE);
		mImage = new ImageView(context);
		mImage.setScaleType(ImageView.ScaleType.MATRIX);
		addView(mImage, new LayoutParams(-1, 0, 1));
		mStatus = new TextView(context);
		mStatus.setTextColor(Color.BLACK);
		mStatus.setText(R.string.eink_loading_image);
		addView(mStatus);
		final LinearLayout buttons = new LinearLayout(context);
		mPrevious = button(buttons, R.string.eink_previous, () -> turn(-1));
		mMode = button(buttons, R.string.eink_original, () -> {
			mOriginal = !mOriginal;
			rebuild();
		});
		mNext = button(buttons, R.string.eink_next, () -> turn(1));
		final Button more = new Button(context);
		more.setText(R.string.eink_actions);
		more.setOnClickListener(actions);
		buttons.addView(more, new LayoutParams(0, -2, 1));
		addView(buttons);
		final LinearLayout album = new LinearLayout(context);
		if(previousImage != null && nextImage != null) {
			button(album, R.string.eink_previous_image, previousImage);
			button(album, R.string.eink_next_image, nextImage);
			addView(album);
		}
		mMode.setEnabled(false);
		mPrevious.setEnabled(false);
		mNext.setEnabled(false);
		mImage.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
			if(r - l != or - ol || b - t != ob - ot) {
				rebuild();
			}
		});
		mWorker.execute(() -> load(factory));
	}

	private Button button(final LinearLayout row, final int label, final Runnable action) {
		final Button button = new Button(getContext());
		button.setText(label);
		button.setOnClickListener(v -> action.run());
		row.addView(button, new LayoutParams(0, -2, 1));
		return button;
	}

	private void load(final GenericFactory<SeekableInputStream, IOException> factory) {
		try {
			mFile = File.createTempFile("eink-image-", ".data", getContext().getCacheDir());
			try(InputStream input = factory.create();
					FileOutputStream output = new FileOutputStream(mFile)) {
				final byte[] bytes = new byte[16_384];
				int count;
				long total = 0;
				while(!mDisposed && (count = input.read(bytes)) > 0) {
					total += count;
					if(total > 128L * 1024 * 1024) {
						throw new IOException("Image file exceeds cache limit");
					}
					output.write(bytes, 0, count);
				}
			}
			if(mDisposed) {
				return;
			}
			try {
				mDecoder = BitmapRegionDecoder.newInstance(mFile.getAbsolutePath(), false);
				mWidth = mDecoder.getWidth();
				mHeight = mDecoder.getHeight();
			} catch(final IOException exception) {
				final BitmapFactory.Options options = new BitmapFactory.Options();
				options.inJustDecodeBounds = true;
				BitmapFactory.decodeFile(mFile.getAbsolutePath(), options);
				options.inJustDecodeBounds = false;
				options.inSampleSize = 1;
				while((long)(options.outWidth / options.inSampleSize)
						* (options.outHeight / options.inSampleSize) > 2_000_000) {
					options.inSampleSize *= 2;
				}
				mPreview = BitmapFactory.decodeFile(mFile.getAbsolutePath(), options);
				if(mPreview == null) {
					throw new IOException("Image cannot be decoded", exception);
				}
				mWidth = mPreview.getWidth();
				mHeight = mPreview.getHeight();
			}
			mMain.post(() -> {
				if(!mDisposed) {
					mMode.setEnabled(mDecoder != null);
					rebuild();
				}
			});
		} catch(final IOException | RuntimeException exception) {
			mMain.post(() -> {
				if(!mDisposed) {
					mStatus.setText(R.string.eink_image_failed);
				}
			});
		}
	}

	private void rebuild() {
		if(mWidth <= 0 || mHeight <= 0 || mImage.getWidth() <= 0
				|| mImage.getHeight() <= 0 || mDisposed) {
			return;
		}
		final int[] anchor = mPages == null ? new int[]{0, 0} : mPages.region(mIndex);
		mPages = new ImagePages(mWidth, mHeight, mImage.getWidth(), mImage.getHeight(),
				mOriginal, Math.round(20 * getResources().getDisplayMetrics().density));
		mIndex = mPages.indexAt(anchor[0], anchor[1]);
		mMode.setText(mOriginal ? R.string.eink_fit_width : R.string.eink_original);
		show();
	}

	public boolean press(final int direction) {
		return (direction < 0 ? mPrevious : mNext).performClick();
	}

	private void turn(final int direction) {
		if(mPages == null || mDisposed) {
			return;
		}
		mIndex = Math.max(0, Math.min(mPages.count() - 1, mIndex + direction));
		show();
	}

	private void show() {
		final int request = ++mGeneration;
		final ImagePages pages = mPages;
		final int index = mIndex;
		mPrevious.setEnabled(index > 0);
		mNext.setEnabled(index + 1 < pages.count());
		mStatus.setText(getResources().getString(R.string.eink_image_page,
				index + 1, pages.count(), index / pages.columns + 1, index % pages.columns + 1));
		mWorker.execute(() -> {
			if(mDisposed || request != mGeneration) {
				return;
			}
			try {
				final int[] area = pages.region(index);
				final String key = pages.tileWidth + ":" + pages.tileHeight
						+ ":" + pages.sample() + ":" + index;
				Bitmap bitmap = mCache.get(key);
				if(bitmap == null) {
					final BitmapFactory.Options options = new BitmapFactory.Options();
					options.inSampleSize = pages.sample();
					options.inPreferredConfig = Bitmap.Config.ARGB_8888;
					bitmap = mDecoder == null ? Bitmap.createBitmap(mPreview,
							area[0], area[1], area[2] - area[0], area[3] - area[1])
							: mDecoder.decodeRegion(
									new Rect(area[0], area[1], area[2], area[3]), options);
					if(bitmap == null) {
						throw new IllegalStateException("No decoded image region");
					}
					bitmap.setDensity(Bitmap.DENSITY_NONE);
					mCache.put(key, bitmap);
				}
				final Bitmap result = bitmap;
				mMain.post(() -> {
					if(mDisposed || request != mGeneration) {
						return;
					}
					mImage.setImageBitmap(result);
					final float scale = Math.min(pages.scale * pages.sample(),
							Math.min((float)mImage.getWidth() / result.getWidth(),
									(float)mImage.getHeight() / result.getHeight()));
					final Matrix matrix = new Matrix();
					matrix.setScale(scale, scale);
					mImage.setImageMatrix(matrix);
				});
			} catch(final RuntimeException exception) {
				mMain.post(() -> {
					if(!mDisposed && request == mGeneration) {
						mStatus.setText(R.string.eink_image_failed);
					}
				});
			}
		});
	}

	public void dispose() {
		if(mDisposed) {
			return;
		}
		mDisposed = true;
		mGeneration++;
		mImage.setImageDrawable(null);
		mWorker.execute(() -> {
			if(mDecoder != null) {
				mDecoder.recycle();
			}
			mCache.evictAll();
			if(mFile != null && mFile.exists() && !mFile.delete()) {
				mFile.deleteOnExit();
			}
		});
		mWorker.shutdown();
	}
}
