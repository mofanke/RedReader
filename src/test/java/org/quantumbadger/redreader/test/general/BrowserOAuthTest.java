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
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quantumbadger.redreader.R;
import org.quantumbadger.redreader.activities.OAuthLoginActivity;
import org.quantumbadger.redreader.activities.OAuthRedirectActivity;
import org.quantumbadger.redreader.common.General;
import org.quantumbadger.redreader.common.GlobalConfig;
import org.quantumbadger.redreader.reddit.api.BrowserOAuthSession;
import org.quantumbadger.redreader.reddit.api.OAuthBrowser;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

/** 验证浏览器登录、生命周期与回跳契约，不访问真实账号。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BrowserOAuthTest {
	private static final String CLIENT = "test-client-id";

	@Before
	public void setup() {
		new EdgeToEdgeInsetsTest().initPrefs();
		GlobalConfig.appId = null;
		General.getSharedPrefs(RuntimeEnvironment.getApplication()).edit()
				.putString(RuntimeEnvironment.getApplication().getString(
						R.string.pref_reddit_client_id_override_key), CLIENT).apply();
		new BrowserOAuthSession(RuntimeEnvironment.getApplication()).clear();
	}

	private static Intent genericBrowser() {
		return new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org/"))
				.addCategory(Intent.CATEGORY_BROWSABLE);
	}

	private static void addBrowser(final String name) {
		addBrowser(name, genericBrowser(), true);
	}

	private static void addBrowser(final String name, final Intent intent, final boolean isDefault) {
		final ResolveInfo info = new ResolveInfo();
		info.activityInfo = new ActivityInfo();
		info.activityInfo.packageName = name;
		info.activityInfo.name = name + ".Browser";
		info.activityInfo.exported = true;
		info.isDefault = isDefault;
		Shadows.shadowOf(RuntimeEnvironment.getApplication().getPackageManager())
				.addResolveInfoForIntent(intent, info);
	}

	private static <T extends View> T findView(final View view, final Class<T> type) {
		if(type.isInstance(view)) {
			return type.cast(view);
		}
		if(view instanceof ViewGroup) {
			final ViewGroup group = (ViewGroup)view;
			for(int i = 0; i < group.getChildCount(); i++) {
				final T found = findView(group.getChildAt(i), type);
				if(found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static Button findButton(final View view, final String text) {
		if(view instanceof Button && text.contentEquals(((Button)view).getText())) {
			return (Button)view;
		}
		if(view instanceof ViewGroup) {
			final ViewGroup group = (ViewGroup)view;
			for(int i = 0; i < group.getChildCount(); i++) {
				final Button found = findButton(group.getChildAt(i), text);
				if(found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static void clearClientId() {
		General.getSharedPrefs(RuntimeEnvironment.getApplication()).edit()
				.putString(RuntimeEnvironment.getApplication().getString(
						R.string.pref_reddit_client_id_override_key), "").apply();
	}

	private static AlertDialog latestDialog() {
		return (AlertDialog)ShadowDialog.getLatestDialog();
	}

	public static class RestrictedLoginActivity extends OAuthLoginActivity {
		@Override
		public void startActivity(final Intent intent) {
			if("test.blocked.browser".equals(intent.getPackage())) {
				throw new SecurityException("Simulated disabled browser");
			}
			super.startActivity(intent);
		}
	}

	private static Uri response(final String state) {
		return Uri.parse("redreader://rr_oauth_redir").buildUpon()
				.appendQueryParameter("state", state).appendQueryParameter("code", "test-code")
				.build();
	}

	private static boolean containsWebView(final View view) {
		if(view instanceof WebView) {
			return true;
		}
		if(view instanceof ViewGroup) {
			final ViewGroup group = (ViewGroup)view;
			for(int i = 0; i < group.getChildCount(); i++) {
				if(containsWebView(group.getChildAt(i))) {
					return true;
				}
			}
		}
		return false;
	}

	@Test
	public void loginUsesRealBrowserAndAllowsRedirectBack() {
		addBrowser("com.android.chrome");
		final OAuthLoginActivity activity = Robolectric.buildActivity(OAuthLoginActivity.class)
				.setup().get();
		final Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
		Assert.assertEquals("com.android.chrome", intent.getPackage());
		Assert.assertEquals("www.reddit.com", intent.getData().getHost());
		Assert.assertEquals(CLIENT, intent.getData().getQueryParameter("client_id"));
		Assert.assertNotEquals("Texas", intent.getData().getQueryParameter("state"));
		Assert.assertFalse(intent.getBooleanExtra(
				"android.support.customtabs.extra.SEND_TO_EXTERNAL_HANDLER", false));
		Assert.assertFalse(intent.getBooleanExtra(
				"androidx.browser.customtabs.extra.ENABLE_EPHEMERAL_BROWSING", false));
		Assert.assertEquals(0, intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK);
		Assert.assertFalse(containsWebView(activity.findViewById(android.R.id.content)));
	}

	@Test
	public void missingClientIdDoesNotOpenInvalidAuthorization() {
		addBrowser("com.android.chrome");
		General.getSharedPrefs(RuntimeEnvironment.getApplication()).edit()
				.putString(RuntimeEnvironment.getApplication().getString(
						R.string.pref_reddit_client_id_override_key), "").apply();
		final OAuthLoginActivity activity = Robolectric.buildActivity(OAuthLoginActivity.class)
				.setup().get();
		Assert.assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
	}

	@Test
	public void missingClientIdShowsInputAndSavingStartsBrowser() {
		clearClientId();
		addBrowser("com.android.chrome");
		final OAuthLoginActivity activity = Robolectric.buildActivity(OAuthLoginActivity.class)
				.setup().get();
		final AlertDialog dialog = latestDialog();
		Assert.assertNotNull(dialog);
		Assert.assertTrue(dialog.isShowing());
		final EditText input = findView(dialog.getWindow().getDecorView(), EditText.class);
		Assert.assertNotNull(input);
		input.setText("  " + CLIENT + "  ");
		dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
		final Intent browser = Shadows.shadowOf(activity).getNextStartedActivity();
		Assert.assertEquals(CLIENT, browser.getData().getQueryParameter("client_id"));
		Assert.assertFalse(dialog.isShowing());
	}

	@Test
	public void emptyClientIdKeepsDialogOpenAndShowsError() {
		clearClientId();
		final OAuthLoginActivity activity = Robolectric.buildActivity(OAuthLoginActivity.class)
				.setup().get();
		final AlertDialog dialog = latestDialog();
		dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
		Assert.assertTrue(dialog.isShowing());
		Assert.assertNotNull(findView(dialog.getWindow().getDecorView(), EditText.class).getError());
		Assert.assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
	}

	@Test
	public void noBrowserShowsVisibleError() {
		final OAuthLoginActivity activity = Robolectric.buildActivity(OAuthLoginActivity.class)
				.setup().get();
		Assert.assertTrue(latestDialog().isShowing());
		Assert.assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
		Assert.assertTrue(new BrowserOAuthSession(activity).consume(response("unused"), CLIENT)
				== BrowserOAuthSession.Result.INVALID);
	}

	@Test
	public void browserWithoutDefaultCategoryUsesExplicitActivity() {
		addBrowser("test.oem.browser", genericBrowser(), false);
		final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
		final Intent intent = OAuthBrowser.intent(activity, Uri.parse("https://www.reddit.com/"), false);
		Assert.assertNotNull(intent);
		Assert.assertEquals("test.oem.browser.Browser", intent.getComponent().getClassName());
		Assert.assertNull(intent.getExtras());
	}

	@Test
	public void browserRoleProvidesPackageFallback() {
		addBrowser("test.oem.browser", new Intent(Intent.ACTION_MAIN)
				.addCategory(Intent.CATEGORY_APP_BROWSER), false);
		final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
		final Intent intent = OAuthBrowser.intent(activity, Uri.parse("https://www.reddit.com/"), false);
		Assert.assertNotNull(intent);
		Assert.assertEquals("test.oem.browser", intent.getPackage());
	}

	@Test
	public void restrictedBrowserFallsBackToNextBrowser() {
		addBrowser("test.blocked.browser");
		addBrowser("com.android.chrome");
		final RestrictedLoginActivity activity = Robolectric.buildActivity(RestrictedLoginActivity.class)
				.setup().get();
		Assert.assertEquals("com.android.chrome",
				Shadows.shadowOf(activity).getNextStartedActivity().getPackage());
	}

	@Test
	public void failedBrowserLaunchShowsErrorWithoutCrashing() {
		addBrowser("test.blocked.browser");
		final RestrictedLoginActivity activity = Robolectric.buildActivity(RestrictedLoginActivity.class)
				.setup().get();
		Assert.assertTrue(latestDialog().isShowing());
		Assert.assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
	}

	@Test
	public void manualChoiceStartsSelectedBrowser() {
		addBrowser("com.android.chrome");
		addBrowser("org.mozilla.firefox");
		final OAuthLoginActivity activity = Robolectric.buildActivity(OAuthLoginActivity.class)
				.setup().get();
		Shadows.shadowOf(activity).getNextStartedActivity();
		final Button choose = findButton(activity.findViewById(android.R.id.content),
				activity.getString(R.string.oauth_browser_choose));
		Assert.assertNotNull(choose);
		choose.performClick();
		final AlertDialog dialog = latestDialog();
		Assert.assertEquals(2, dialog.getListView().getCount());
		dialog.getListView().performItemClick(null, 1, 1);
		Assert.assertEquals("org.mozilla.firefox",
				Shadows.shadowOf(activity).getNextStartedActivity().getPackage());
	}

	@Test
	public void validCallbackReturnsOriginalAccountResultOnce() {
		addBrowser("com.android.chrome");
		final ActivityController<OAuthLoginActivity> controller
				= Robolectric.buildActivity(OAuthLoginActivity.class).setup();
		final OAuthLoginActivity activity = controller.get();
		final String state = Shadows.shadowOf(activity).getNextStartedActivity()
				.getData().getQueryParameter("state");
		final Uri uri = response(state);
		controller.newIntent(new Intent().putExtra(OAuthLoginActivity.RESPONSE, uri.toString()));
		Assert.assertEquals(123, Shadows.shadowOf(activity).getResultCode());
		Assert.assertEquals(uri.toString(), Shadows.shadowOf(activity).getResultIntent()
				.getStringExtra("url"));
		Assert.assertFalse(new BrowserOAuthSession(activity).recognizes(uri, CLIENT));
	}

	@Test
	public void redirectReturnsToWaitingActivityWithoutNewTask() {
		final String state = new BrowserOAuthSession(RuntimeEnvironment.getApplication()).begin(CLIENT);
		final Uri uri = response(state);
		final OAuthRedirectActivity activity = Robolectric.buildActivity(OAuthRedirectActivity.class,
				new Intent(Intent.ACTION_VIEW, uri)).setup().get();
		final Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
		Assert.assertEquals(OAuthLoginActivity.class.getName(), next.getComponent().getClassName());
		Assert.assertEquals(uri.toString(), next.getStringExtra(OAuthLoginActivity.RESPONSE));
		Assert.assertTrue((next.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
		Assert.assertTrue((next.getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0);
		Assert.assertEquals(0, next.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK);
	}

	@Test
	public void implicitRedirectResolvesDedicatedHandler() {
		final Intent intent = new Intent(Intent.ACTION_VIEW, response("test-state"))
				.addCategory(Intent.CATEGORY_BROWSABLE);
		final ResolveInfo resolved = RuntimeEnvironment.getApplication().getPackageManager()
				.resolveActivity(intent, 0);
		Assert.assertNotNull(resolved);
		Assert.assertEquals(OAuthRedirectActivity.class.getName(), resolved.activityInfo.name);
	}

	@Test
	public void invalidStateDoesNotCancelLegitimatePendingLogin() {
		final BrowserOAuthSession session = new BrowserOAuthSession(RuntimeEnvironment.getApplication());
		final String state = session.begin(CLIENT);
		Assert.assertEquals(BrowserOAuthSession.Result.INVALID,
				session.consume(response("different-state"), CLIENT));
		Assert.assertTrue(session.recognizes(response(state), CLIENT));
	}

	@Test
	public void retryGeneratesNewStateAndOldCallbackIsRejected() {
		final BrowserOAuthSession session = new BrowserOAuthSession(RuntimeEnvironment.getApplication());
		final String first = session.begin(CLIENT);
		final String second = session.begin(CLIENT);
		Assert.assertNotEquals(first, second);
		Assert.assertFalse(session.recognizes(response(first), CLIENT));
		Assert.assertTrue(session.recognizes(response(second), CLIENT));
	}

	@Test
	public void changedClientAndExpiredLoginAreRejected() {
		final BrowserOAuthSession session = new BrowserOAuthSession(RuntimeEnvironment.getApplication());
		final Uri uri = response(session.begin(CLIENT));
		Assert.assertFalse(session.recognizes(uri, "another-test-client"));
		RuntimeEnvironment.getApplication().getSharedPreferences(BrowserOAuthSession.STORE, 0)
				.edit().putLong("started", 0).apply();
		Assert.assertFalse(session.recognizes(uri, CLIENT));
	}

	@Test
	public void callbackAddressAndDuplicateParametersAreChecked() {
		Assert.assertFalse(BrowserOAuthSession.isRedirect(Uri.parse("http://rr_oauth_redir")));
		Assert.assertFalse(BrowserOAuthSession.isRedirect(Uri.parse("redreader://rr_oauth_redir.evil")));
		Assert.assertFalse(BrowserOAuthSession.isRedirect(Uri.parse("redreader://rr_oauth_redir/path")));
		Assert.assertFalse(BrowserOAuthSession.isRedirect(Uri.parse("redreader://rr_oauth_redir#x")));
		final BrowserOAuthSession session = new BrowserOAuthSession(RuntimeEnvironment.getApplication());
		final String state = session.begin(CLIENT);
		Assert.assertFalse(session.recognizes(response(state).buildUpon()
				.appendQueryParameter("state", state).build(), CLIENT));
		Assert.assertEquals(BrowserOAuthSession.Result.INVALID, session.consume(response(state)
				.buildUpon().appendQueryParameter("code", "duplicate-test-code").build(), CLIENT));
	}

	@Test
	public void denialDoesNotReturnSuccess() {
		final BrowserOAuthSession session = new BrowserOAuthSession(RuntimeEnvironment.getApplication());
		final Uri uri = Uri.parse("redreader://rr_oauth_redir").buildUpon()
				.appendQueryParameter("state", session.begin(CLIENT))
				.appendQueryParameter("error", "access_denied").build();
		Assert.assertEquals(BrowserOAuthSession.Result.DENIED, session.consume(uri, CLIENT));
		Assert.assertEquals(BrowserOAuthSession.Result.INVALID, session.consume(uri, CLIENT));
	}

	@Test
	public void restoringWaitingScreenPreservesLoginState() {
		addBrowser("com.android.chrome");
		final ActivityController<OAuthLoginActivity> original
				= Robolectric.buildActivity(OAuthLoginActivity.class).setup();
		final String state = Shadows.shadowOf(original.get()).getNextStartedActivity()
				.getData().getQueryParameter("state");
		final Bundle saved = new Bundle();
		original.saveInstanceState(saved).pause().stop().destroy();
		final OAuthLoginActivity restored = Robolectric.buildActivity(OAuthLoginActivity.class)
				.create(saved).start().resume().get();
		Assert.assertNull(Shadows.shadowOf(restored).getNextStartedActivity());
		Assert.assertTrue(new BrowserOAuthSession(restored).recognizes(response(state), CLIENT));
	}

	@Test
	public void torModeDoesNotOpenNonTorBrowser() {
		addBrowser("com.android.chrome");
		final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
		Assert.assertNull(OAuthBrowser.intent(activity, Uri.parse("https://www.reddit.com/"), true));
		addBrowser("org.torproject.torbrowser");
		Assert.assertEquals("org.torproject.torbrowser", OAuthBrowser.intent(activity,
				Uri.parse("https://www.reddit.com/"), true).getPackage());
	}
}
