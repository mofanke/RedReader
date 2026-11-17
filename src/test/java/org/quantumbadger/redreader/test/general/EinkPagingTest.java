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


package org.quantumbadger.redreader.test.general;

import android.app.Activity;
import android.os.Looper;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quantumbadger.redreader.eink.EinkKeys;
import org.quantumbadger.redreader.eink.EinkRecyclerView;
import org.quantumbadger.redreader.eink.ImagePages;
import org.quantumbadger.redreader.activities.ViewsBaseActivity;
import org.quantumbadger.redreader.common.PrefsUtility;
import org.quantumbadger.redreader.views.InlinePreviewHolderView;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import java.util.HashSet;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class EinkPagingTest {
	public static class ReadingActivity extends ViewsBaseActivity {
		private EinkRecyclerView mList;

		public boolean press(final int direction) {
			return super.einkPress(direction);
		}

		@Override
		protected void onCreate(final Bundle state) {
			PrefsUtility.applyTheme(this);
			super.onCreate(state);
			mList = new EinkRecyclerView(this, null);
			mList.setLayoutManager(new LinearLayoutManager(this));
			mList.setAdapter(new TextAdapter());
			setBaseActivityListing(mList);
		}
	}

	@Test
	public void activityPageKeyAndArrowUseTheSameNativeCallback() {
		new EdgeToEdgeInsetsTest().initPrefs();
		final ReadingActivity activity = Robolectric.buildActivity(ReadingActivity.class)
				.setup().visible().get();
		layout(activity.mList);
		activity.press(1);
		layout(activity.mList);
		final LinearLayoutManager manager = (LinearLayoutManager)activity.mList.getLayoutManager();
		final int virtualPosition = manager.findFirstVisibleItemPosition();
		activity.press(-1);
		layout(activity.mList);
		Assert.assertEquals(0, manager.findFirstVisibleItemPosition());
		Assert.assertTrue(activity.dispatchKeyEvent(new KeyEvent(0, 93)));
		layout(activity.mList);
		Assert.assertEquals(virtualPosition, manager.findFirstVisibleItemPosition());
		Assert.assertTrue(activity.dispatchKeyEvent(new KeyEvent(0, 93)));
		Assert.assertTrue(activity.dispatchKeyEvent(new KeyEvent(1, 93)));
		layout(activity.mList);
		Assert.assertEquals(virtualPosition, manager.findFirstVisibleItemPosition());
	}
	@Test
	public void hardwareKeysUseOneButtonCallAndConsumeTheWholeSequence() {
		final EinkKeys keys = new EinkKeys();
		final int[] calls = {0};
		Assert.assertTrue(keys.dispatch(93, 0, false, true, () -> { calls[0]++; return false; }));
		Assert.assertTrue(keys.dispatch(93, 0, false, true, () -> { calls[0]++; return true; }));
		Assert.assertTrue(keys.dispatch(93, 2, false, true, () -> { calls[0]++; return true; }));
		Assert.assertTrue(keys.dispatch(93, 1, false, true, () -> { calls[0]++; return true; }));
		Assert.assertEquals(1, calls[0]);
		Assert.assertFalse(keys.dispatch(93, 0, true, true, () -> { calls[0]++; return true; }));
		Assert.assertFalse(keys.dispatch(25, 0, false, false, () -> true));
		Assert.assertTrue(keys.dispatch(92, 2, false, true, () -> { calls[0]++; return true; }));
		Assert.assertEquals(2, calls[0]);
	}

	private static final class TextAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
				final int type) {
			final TextView text = new TextView(parent.getContext());
			text.setTextSize(20);
			text.setLayoutParams(new RecyclerView.LayoutParams(-1, 40));
			return new RecyclerView.ViewHolder(text) {};
		}

		@Override
		public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder,
				final int position) {
			((TextView)holder.itemView).setText("Reading row " + position);
		}

		@Override
		public int getItemCount() {
			return 100;
		}
	}

	private static void layout(final EinkRecyclerView list) {
		for(int pass = 0; pass < 3; pass++) {
			Shadows.shadowOf(Looper.getMainLooper()).idle();
			final View root = list.getRootView();
			final int width = Math.max(1, root.getWidth());
			final int height = Math.max(1, root.getHeight());
			root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
					View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
			root.layout(0, 0, width, height);
			root.getViewTreeObserver().dispatchOnPreDraw();
		}
		Shadows.shadowOf(Looper.getMainLooper()).idle();
	}

	@Test
	public void inlinePictureIsFullyVisibleOnOnePage() {
		new EdgeToEdgeInsetsTest().initPrefs();
		final Activity activity = Robolectric.buildActivity(Activity.class).setup().visible().get();
		final EinkRecyclerView list = new EinkRecyclerView(activity, null);
		list.setLayoutManager(new LinearLayoutManager(activity));
		list.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
			@NonNull
			@Override
			public RecyclerView.ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
					final int type) {
				final LinearLayout row = new LinearLayout(parent.getContext());
				row.setOrientation(LinearLayout.VERTICAL);
				row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
				final TextView before = new TextView(parent.getContext());
				before.setText("Before image");
				row.addView(before, new LinearLayout.LayoutParams(-1, 500));
				final InlinePreviewHolderView image = new InlinePreviewHolderView(parent.getContext());
				image.setTag("picture");
				image.setImageSize(400, 1000);
				row.addView(image, new LinearLayout.LayoutParams(-1, -2));
				return new RecyclerView.ViewHolder(row) {};
			}

			@Override
			public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder,
					final int position) {}

			@Override
			public int getItemCount() {
				return 1;
			}
		});
		activity.setContentView(list);
		boolean seen = false;
		for(int page = 0; page < 5; page++) {
			layout(list);
			final View image = list.findViewWithTag("picture");
			final android.graphics.Rect rect = new android.graphics.Rect(
					0, 0, image.getWidth(), image.getHeight());
			list.offsetDescendantRectToMyCoords(image, rect);
			if(rect.bottom > 0 && rect.top < list.getHeight()) {
				Assert.assertTrue("Image must start within the page", rect.top >= 0);
				Assert.assertTrue("Image must end within the same page: " + rect.toShortString()
						+ " viewport=" + list.getHeight(), rect.bottom <= list.getHeight());
				seen = true;
				break;
			}
			list.turn(1);
		}
		Assert.assertTrue("Image must appear within five pages", seen);
	}

	@Test
	public void nativeListDoesNotLoseRowsOrBackfillItsLastPage() {
		final Activity activity = Robolectric.buildActivity(Activity.class).setup().visible().get();
		final EinkRecyclerView list = new EinkRecyclerView(activity, null);
		final LinearLayoutManager manager = new LinearLayoutManager(activity);
		list.setLayoutManager(manager);
		list.setAdapter(new TextAdapter());
		activity.setContentView(list);
		layout(list);
		final Set<Integer> rows = new HashSet<>();
		int previous = -1;
		for(int page = 0; page < 30; page++) {
			for(int i = 0; i < list.getChildCount(); i++) {
				final View child = list.getChildAt(i);
				if(child.getTop() >= 0 && child.getBottom() <= list.getHeight()) {
					rows.add(list.getChildAdapterPosition(child));
				}
			}
			final int position = manager.findFirstVisibleItemPosition();
			if(position == previous) {
				break;
			}
			previous = position;
			list.turn(1);
			layout(list);
		}
		Assert.assertEquals(100, rows.size());
		Assert.assertTrue("The short last page must retain its normal start", previous >= 90);
		final int lastStart = previous;
		list.turn(-1);
		layout(list);
		Assert.assertTrue(manager.findFirstVisibleItemPosition() < lastStart);
		list.turn(1);
		layout(list);
		Assert.assertEquals(lastStart, manager.findFirstVisibleItemPosition());
	}

	@Test
	public void imagePagesCoverLongAndWideImagesAndKeepTheShortTail() {
		final ImagePages tail = new ImagePages(1000, 1605, 1000, 1400, true, 20);
		final int[] area = tail.region(tail.count() - 1);
		Assert.assertEquals(1380, area[1]);
		Assert.assertEquals(225, area[3] - area[1]);
		for(final boolean original : new boolean[]{false, true}) {
			for(final int width : new int[]{300, 1600, 6000}) {
				final ImagePages pages = new ImagePages(width, 100_000, 1264, 1400, original, 20);
				for(int i = 0; i < pages.count(); i++) {
					final int[] region = pages.region(i);
					Assert.assertTrue(region[0] < region[2] && region[1] < region[3]);
					if(i % pages.columns > 0) {
						Assert.assertTrue(pages.region(i - 1)[2] >= region[0]);
					}
					if(i >= pages.columns) {
						Assert.assertTrue(pages.region(i - pages.columns)[3] >= region[1]);
					}
				}
				final int[] last = pages.region(pages.count() - 1);
				Assert.assertTrue((long)(pages.tileWidth / pages.sample())
						* (pages.tileHeight / pages.sample()) <= 4_000_000);
				Assert.assertEquals(width, last[2]);
				Assert.assertEquals(100_000, last[3]);
			}
		}
	}
}
