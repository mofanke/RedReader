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
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.quantumbadger.redreader.views.InlinePreviewHolderView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** 保留文字重叠、整块图片、实际返回起点和文末空白的原生列表。 */
public final class EinkRecyclerView extends RecyclerView {
	private static WeakReference<EinkRecyclerView> sTouched = new WeakReference<>(null);
	private final List<Anchor> mHistory = new ArrayList<>();
	private int mIndex;
	private boolean mPaging;
	private Anchor mExpected;
	private int mRevision;
	private boolean mTailQueued;
	private final AdapterDataObserver mTailObserver = new AdapterDataObserver() {
		@Override
		public void onChanged() {
			refreshTail(true);
		}

		@Override
		public void onItemRangeInserted(final int start, final int count) {
			refreshTail(false);
		}

		@Override
		public void onItemRangeRemoved(final int start, final int count) {
			refreshTail(true);
		}

		@Override
		public void onItemRangeMoved(final int from, final int to, final int count) {
			refreshTail(true);
		}

		@Override
		public void onItemRangeChanged(final int start, final int count) {
			refreshTail(false);
		}
	};

	private void refreshTail(final boolean reset) {
		if(reset) {
			mRevision++;
			mHistory.clear();
			mIndex = 0;
			mExpected = null;
		}
		if(!mTailQueued) {
			mTailQueued = true;
			post(() -> {
				mTailQueued = false;
				invalidateItemDecorations();
			});
		}
	}

	@Override
	public void setAdapter(@Nullable final Adapter adapter) {
		final Adapter previous = getAdapter();
		if(previous != null) {
			previous.unregisterAdapterDataObserver(mTailObserver);
		}
		super.setAdapter(adapter);
		if(adapter != null) {
			adapter.registerAdapterDataObserver(mTailObserver);
		}
		refreshTail(true);
	}

	private static final class Anchor {
		private final int position;
		private final int offset;

		private Anchor(final int position, final int offset) {
			this.position = position;
			this.offset = offset;
		}

		private boolean same(final Anchor other) {
			return other != null && position == other.position
					&& Math.abs(offset - other.offset) <= 2;
		}
	}

	public EinkRecyclerView(final Context context, @Nullable final AttributeSet attrs) {
		super(context, attrs);
		setClipToPadding(false);
		addItemDecoration(new ItemDecoration() {
			@Override
			public void getItemOffsets(@NonNull final Rect out, @NonNull final View view,
					@NonNull final RecyclerView parent, @NonNull final State state) {
				if(EinkPreferences.enabled(context)
						&& parent.getChildAdapterPosition(view) == state.getItemCount() - 1) {
					out.bottom = Math.max(0, getHeight() - getPaddingBottom() - getPaddingTop());
				}
			}
		});
		getViewTreeObserver().addOnPreDrawListener(() -> {
			if(!EinkPreferences.enabled(getContext())) {
				return true;
			}
			setItemAnimator(null);
			return !prepareImages();
		});
	}

	public static EinkRecyclerView lastTouched() {
		return sTouched.get();
	}

	@Override
	public boolean dispatchTouchEvent(final MotionEvent event) {
		if(event.getActionMasked() == MotionEvent.ACTION_DOWN) {
			sTouched = new WeakReference<>(this);
		}
		return super.dispatchTouchEvent(event);
	}

	@Override
	protected void onSizeChanged(final int width, final int height,
			final int oldWidth, final int oldHeight) {
		super.onSizeChanged(width, height, oldWidth, oldHeight);
		if(height != oldHeight || width != oldWidth) {
			mRevision++;
			post(() -> {
				for(final InlinePreviewHolderView image : images()) {
					if(image.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
						final ViewGroup.MarginLayoutParams params
								= (ViewGroup.MarginLayoutParams)image.getLayoutParams();
						params.topMargin = 0;
						image.setLayoutParams(params);
					}
				}
				invalidateItemDecorations();
			});
			mHistory.clear();
			mExpected = null;
		}
	}

	private Anchor anchor(final LinearLayoutManager manager) {
		final int first = manager.findFirstVisibleItemPosition();
		final View view = manager.findViewByPosition(first);
		return view == null ? null
				: new Anchor(first, manager.getDecoratedTop(view) - getPaddingTop());
	}

	private List<InlinePreviewHolderView> images() {
		final List<InlinePreviewHolderView> result = new ArrayList<>();
		collectImages(this, result);
		return result;
	}

	private static void collectImages(final View view,
			final List<InlinePreviewHolderView> result) {
		if(!view.isShown()) {
			return;
		}
		if(view instanceof InlinePreviewHolderView) {
			result.add((InlinePreviewHolderView)view);
		} else if(view instanceof ViewGroup) {
			final ViewGroup group = (ViewGroup)view;
			for(int i = 0; i < group.getChildCount(); i++) {
				collectImages(group.getChildAt(i), result);
			}
		}
	}

	private Rect bounds(final View image) {
		final Rect rect = new Rect(0, 0, image.getWidth(), image.getHeight());
		offsetDescendantRectToMyCoords(image, rect);
		return rect;
	}

	private boolean prepareImages() {
		final int end = getHeight() - getPaddingBottom();
		for(final InlinePreviewHolderView image : images()) {
			final Rect rect = bounds(image);
			if(rect.top >= getPaddingTop() && rect.top < end && rect.bottom > end) {
				final ViewGroup.LayoutParams params = image.getLayoutParams();
				if(params instanceof ViewGroup.MarginLayoutParams) {
					final ViewGroup.MarginLayoutParams margins
							= (ViewGroup.MarginLayoutParams)params;
					margins.topMargin += end - rect.top + 1;
					image.setLayoutParams(margins);
					return true;
				}
			}
		}
		return false;
	}

	public boolean turn(final int direction) {
		if(mPaging || !(getLayoutManager() instanceof LinearLayoutManager)) {
			return true;
		}
		final LinearLayoutManager manager = (LinearLayoutManager)getLayoutManager();
		final Anchor current = anchor(manager);
		if(current == null) {
			return true;
		}
		if(!current.same(mExpected) || mHistory.isEmpty()) {
			mHistory.clear();
			mHistory.add(current);
			mIndex = 0;
		}
		final View last = manager.findViewByPosition(manager.getItemCount() - 1);
		final int top = getPaddingTop();
		final int bottom = getHeight() - getPaddingBottom();
		if(direction > 0 && last != null && last.getBottom() <= bottom) {
			return true;
		}
		stopScroll();
		mPaging = true;
		final int revision = mRevision;
		final boolean record = direction > 0 && mIndex + 1 >= mHistory.size();
		if(direction < 0 && mIndex > 0) {
			mIndex--;
			final Anchor previous = mHistory.get(mIndex);
			manager.scrollToPositionWithOffset(previous.position, previous.offset);
		} else if(direction > 0 && mIndex + 1 < mHistory.size()) {
			mIndex++;
			final Anchor next = mHistory.get(mIndex);
			manager.scrollToPositionWithOffset(next.position, next.offset);
		} else {
			final int overlap = EinkPager.lineHeight(this) * EinkPreferences.overlap(getContext());
			int distance = Math.max(1, bottom - top - overlap);
			if(direction > 0) {
				for(final InlinePreviewHolderView image : images()) {
					final Rect rect = bounds(image);
					if(rect.top >= bottom && rect.top <= bottom + 2
							&& rect.height() > bottom - top - overlap) {
						distance = Math.max(distance, rect.top - top);
					} else if(rect.top >= top && rect.bottom <= bottom
							&& distance + top > rect.top && distance + top < rect.bottom) {
						distance = rect.bottom - top;
					}
				}
			}
			scrollBy(0, direction * distance);
		}
		post(() -> {
			if(revision != mRevision) {
				mPaging = false;
				return;
			}
			mExpected = anchor(manager);
			if(record && mExpected != null && !current.same(mExpected)) {
				mHistory.add(mExpected);
				mIndex++;
			}
			mPaging = false;
		});
		return true;
	}
}
