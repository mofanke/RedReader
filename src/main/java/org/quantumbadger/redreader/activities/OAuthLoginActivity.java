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

package org.quantumbadger.redreader.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import java.util.List;
import org.quantumbadger.redreader.R;
import org.quantumbadger.redreader.common.General;
import org.quantumbadger.redreader.common.PrefsUtility;
import org.quantumbadger.redreader.common.TorCommon;
import org.quantumbadger.redreader.reddit.api.BrowserOAuthSession;
import org.quantumbadger.redreader.reddit.api.OAuthBrowser;
import org.quantumbadger.redreader.reddit.api.RedditOAuth;

/** 浏览器完成 Google 或密码登录，将有效授权回交现有账号处理流程。 */
public class OAuthLoginActivity extends ViewsBaseActivity {
	public static final String RESPONSE = "oauth_browser_response";
	private BrowserOAuthSession mSession;
	private TextView mStatus;

	@Override
	protected boolean einkUsePagingBar() {
		return false;
	}

	@Override
	public void onCreate(@Nullable final Bundle state) {
		PrefsUtility.applyTheme(this);
		super.onCreate(state);
		mSession = new BrowserOAuthSession(this);
		setTitle(R.string.oauth_browser_title);
		final LinearLayout content = new LinearLayout(this);
		content.setOrientation(LinearLayout.VERTICAL);
		final int padding = General.dpToPixels(this, 16);
		content.setPadding(padding, padding, padding, padding);
		mStatus = new TextView(this);
		mStatus.setText(R.string.oauth_browser_description);
		content.addView(mStatus);
		final Button browser = new Button(this);
		browser.setText(R.string.oauth_browser_continue);
		browser.setOnClickListener(v -> startBrowser(false));
		content.addView(browser);
		final Button choose = new Button(this);
		choose.setText(R.string.oauth_browser_choose);
		choose.setOnClickListener(v -> startBrowser(true));
		content.addView(choose);
		final Button settings = new Button(this);
		settings.setText(R.string.oauth_browser_client_settings);
		settings.setOnClickListener(v -> configureClient(false));
		content.addView(settings);
		final Button cancel = new Button(this);
		cancel.setText(R.string.dialog_cancel);
		cancel.setOnClickListener(v -> {
			mSession.clear();
			finish();
		});
		content.addView(cancel);
		final ScrollView scroll = new ScrollView(this);
		scroll.addView(content);
		setBaseActivityListing(scroll);
		if(getIntent().hasExtra(RESPONSE)) {
			complete(getIntent());
		} else if(state == null) {
			startBrowser(false);
		}
	}

	private void startBrowser(final boolean choose) {
		final String client = RedditOAuth.getConfiguredClientId();
		if(client == null || client.isEmpty() || client.equals("your ID here")) {
			mStatus.setText(R.string.oauth_browser_client_required);
			configureClient(choose);
			return;
		}
		final boolean tor = TorCommon.isTorEnabled();
		final String nonce = mSession.begin(client);
		final List<Intent> browsers = OAuthBrowser.intents(this,
				RedditOAuth.getPromptUri(nonce), tor);
		if(browsers.isEmpty()) {
			mSession.clear();
			showMessage(tor ? R.string.oauth_browser_tor_required
					: R.string.oauth_browser_missing);
			return;
		}
		if(choose) {
			final CharSequence[] labels = new CharSequence[browsers.size()];
			for(int i = 0; i < labels.length; i++) {
				labels[i] = browserLabel(browsers.get(i));
			}
			new AlertDialog.Builder(this).setTitle(R.string.oauth_browser_choose)
					.setItems(labels, (dialog, index) -> {
						if(!launchBrowser(browsers.get(index))) {
							mSession.clear();
							showMessage(R.string.oauth_browser_cannot_open);
						}
					})
					.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> mSession.clear())
					.setOnCancelListener(dialog -> mSession.clear()).show();
			return;
		}
		for(final Intent browser : browsers) {
			if(launchBrowser(browser)) {
				return;
			}
		}
		mSession.clear();
		showMessage(R.string.oauth_browser_cannot_open);
	}

	private boolean launchBrowser(final Intent intent) {
		try {
			startActivity(intent);
			mStatus.setText(R.string.oauth_browser_waiting);
			return true;
		} catch(final ActivityNotFoundException | SecurityException exception) {
			return false;
		}
	}

	private CharSequence browserLabel(final Intent intent) {
		try {
			return getPackageManager().getApplicationLabel(getPackageManager()
					.getApplicationInfo(intent.getPackage(), 0));
		} catch(final PackageManager.NameNotFoundException exception) {
			return intent.getPackage();
		}
	}

	private void showMessage(final int message) {
		mStatus.setText(message);
		new AlertDialog.Builder(this).setTitle(R.string.oauth_browser_title).setMessage(message)
				.setPositiveButton(R.string.dialog_close, null).show();
	}

	private void configureClient(final boolean choose) {
		final EditText input = new EditText(this);
		input.setSingleLine(true);
		input.setInputType(InputType.TYPE_CLASS_TEXT
				| InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
		input.setHint(R.string.oauth_browser_client_id);
		input.setText(RedditOAuth.getConfiguredClientId());
		final AlertDialog dialog = new AlertDialog.Builder(this)
				.setTitle(R.string.oauth_browser_client_id)
				.setMessage(R.string.oauth_browser_client_required)
				.setView(input).setPositiveButton(R.string.oauth_browser_save_continue, null)
				.setNegativeButton(R.string.dialog_cancel, null).create();
		dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
				.setOnClickListener(view -> {
					final String client = input.getText().toString().trim();
					if(client.isEmpty() || client.equals("your ID here")) {
						input.setError(getString(R.string.oauth_browser_client_empty));
						return;
					}
					General.getSharedPrefs(this).edit()
						.putString(getString(R.string.pref_reddit_client_id_override_key), client)
						.apply();
					dialog.dismiss();
					startBrowser(choose);
				}));
		dialog.show();
	}

	@Override
	protected void onNewIntent(final Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		if(intent.hasExtra(RESPONSE)) {
			complete(intent);
		}
	}

	private void complete(final Intent intent) {
		final String text = intent.getStringExtra(RESPONSE);
		final Uri response = text == null ? null : Uri.parse(text);
		final BrowserOAuthSession.Result result = mSession.consume(response,
				RedditOAuth.getConfiguredClientId());
		if(result == BrowserOAuthSession.Result.SUCCESS) {
			setResult(123, new Intent().putExtra("url", response.toString()));
			finish();
		} else {
			mStatus.setText(result == BrowserOAuthSession.Result.DENIED
					? R.string.oauth_browser_denied : R.string.oauth_browser_invalid);
		}
	}

	@Override
	protected void onDestroy() {
		if(isFinishing() && mSession != null) {
			mSession.clear();
		}
		super.onDestroy();
	}
}
