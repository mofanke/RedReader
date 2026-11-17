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
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import org.quantumbadger.redreader.R;
import org.quantumbadger.redreader.common.General;
import org.quantumbadger.redreader.common.PrefsUtility;
import org.quantumbadger.redreader.common.TorCommon;
import org.quantumbadger.redreader.reddit.api.BrowserOAuthSession;
import org.quantumbadger.redreader.reddit.api.OAuthBrowser;
import org.quantumbadger.redreader.reddit.api.RedditOAuth;
import org.quantumbadger.redreader.settings.SettingsActivity;

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
		browser.setOnClickListener(v -> startBrowser());
		content.addView(browser);
		final Button settings = new Button(this);
		settings.setText(R.string.oauth_browser_client_settings);
		settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
		content.addView(settings);
		final Button cancel = new Button(this);
		cancel.setText(R.string.dialog_cancel);
		cancel.setOnClickListener(v -> {
			mSession.clear();
			finish();
		});
		content.addView(cancel);
		setBaseActivityListing(content);
		if(getIntent().hasExtra(RESPONSE)) {
			complete(getIntent());
		} else if(state == null) {
			startBrowser();
		}
	}

	private void startBrowser() {
		final String client = RedditOAuth.getConfiguredClientId();
		if(client == null || client.isEmpty() || client.equals("your ID here")) {
			mStatus.setText(R.string.oauth_browser_client_required);
			return;
		}
		final boolean tor = TorCommon.isTorEnabled();
		final String nonce = mSession.begin(client);
		final Intent intent = OAuthBrowser.intent(this, RedditOAuth.getPromptUri(nonce), tor);
		if(intent == null) {
			mSession.clear();
			mStatus.setText(tor ? R.string.oauth_browser_tor_required
					: R.string.oauth_browser_missing);
			return;
		}
		try {
			startActivity(intent);
			mStatus.setText(R.string.oauth_browser_waiting);
		} catch(final ActivityNotFoundException exception) {
			mSession.clear();
			mStatus.setText(R.string.oauth_browser_missing);
		}
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
