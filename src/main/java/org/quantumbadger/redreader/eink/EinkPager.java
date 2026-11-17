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

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.quantumbadger.redreader.R;
import org.quantumbadger.redreader.common.General;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 屏幕箭头和实体键共用当前可见阅读栏的翻页动作。 */
public final class EinkPager {
	private final Activity mActivity;
	private final View mContent;
	private final Button mPrevious;
	private final Button mNext;
	private final LinearLayout mRoot;
	private String mWebScript;

	public EinkPager(final Activity activity, final View content) {
		mActivity = activity;
		mContent = content;
		mRoot = new LinearLayout(activity);
		mRoot.setOrientation(LinearLayout.VERTICAL);
		mRoot.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
		final LinearLayout buttons = new LinearLayout(activity);
		mPrevious = new Button(activity);
		mNext = new Button(activity);
		mPrevious.setText(R.string.eink_previous);
		mNext.setText(R.string.eink_next);
		mPrevious.setOnClickListener(v -> turn(-1));
		mNext.setOnClickListener(v -> turn(1));
		buttons.addView(mPrevious, new LinearLayout.LayoutParams(0, -2, 1));
		buttons.addView(mNext, new LinearLayout.LayoutParams(0, -2, 1));
		mRoot.addView(buttons);
		General.applySystemBarPadding(buttons);
	}

	public View getView() {
		return mRoot;
	}

	public boolean available() {
		return find(mContent) != null;
	}

	public boolean press(final int direction) {
		return (direction < 0 ? mPrevious : mNext).performClick();
	}

	private View find(final View view) {
		if(!view.isShown() || view.getWidth() <= 0 || view.getHeight() <= 0) {
			return null;
		}
		final EinkRecyclerView touched = EinkRecyclerView.lastTouched();
		if(touched != null && touched.isShown() && contains(view, touched)) {
			return touched;
		}
		final View focused = view.findFocus();
		if(focused != null && focused != view) {
			final View target = find(focused);
			if(target != null) {
				return target;
			}
		}
		if(view instanceof RecyclerView || view instanceof ScrollView || view instanceof WebView) {
			return view;
		}
		if(view instanceof ViewGroup) {
			final ViewGroup group = (ViewGroup)view;
			for(int i = group.getChildCount() - 1; i >= 0; i--) {
				final View target = find(group.getChildAt(i));
				if(target != null) {
					return target;
				}
			}
		}
		return null;
	}

	private static boolean contains(final View parent, final View child) {
		View current = child;
		while(current != null) {
			if(current == parent) {
				return true;
			}
			current = current.getParent() instanceof View ? (View)current.getParent() : null;
		}
		return false;
	}

	public static int lineHeight(final View view) {
		int result = 1;
		if(view instanceof TextView) {
			result = ((TextView)view).getLineHeight();
		}
		if(view instanceof ViewGroup) {
			final ViewGroup group = (ViewGroup)view;
			for(int i = 0; i < group.getChildCount(); i++) {
				final View child = group.getChildAt(i);
				final Rect visible = new Rect();
				if(child.getGlobalVisibleRect(visible)) {
					result = Math.max(result, lineHeight(child));
				}
			}
		}
		return result;
	}

	private void turn(final int direction) {
		final View target = find(mContent);
		if(target == null) {
			return;
		}
		if(target instanceof EinkRecyclerView) {
			((EinkRecyclerView)target).turn(direction);
		} else if(target instanceof WebView) {
			try {
				if(mWebScript == null) {
					final ByteArrayOutputStream data = new ByteArrayOutputStream();
					try(InputStream stream = mActivity.getAssets().open("eink-reading.js")) {
						final byte[] bytes = new byte[8192];
						int count;
						while((count = stream.read(bytes)) > 0) {
							data.write(bytes, 0, count);
						}
					}
					mWebScript = new String(data.toByteArray(), StandardCharsets.UTF_8);
				}
				((WebView)target).evaluateJavascript(mWebScript
						+ "\nHydrogenReadingPager.turn(" + direction + ","
						+ EinkPreferences.overlap(mActivity) + ");", null);
			} catch(final IOException ignore) {
				// 资源不可读时保持当前阅读位置。
			}
		} else {
			final Rect visible = new Rect();
			target.getGlobalVisibleRect(visible);
			final int height = visible.height() - target.getPaddingTop()
					- target.getPaddingBottom();
			final int overlap = lineHeight(target) * EinkPreferences.overlap(mActivity);
			final int distance = Math.max(1, height - overlap) * direction;
			if(target instanceof RecyclerView) {
				((RecyclerView)target).stopScroll();
				((RecyclerView)target).scrollBy(0, distance);
			} else {
				target.scrollBy(0, distance);
			}
		}
	}
}
