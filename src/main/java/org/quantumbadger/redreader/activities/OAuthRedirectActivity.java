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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import org.quantumbadger.redreader.R;
import org.quantumbadger.redreader.reddit.api.BrowserOAuthSession;
import org.quantumbadger.redreader.reddit.api.RedditOAuth;

/** 接收浏览器回跳，回到发起授权的 Activity 并交付原有账号登录回调。 */
public class OAuthRedirectActivity extends Activity {
	@Override
	protected void onCreate(final Bundle state) {
		super.onCreate(state);
		final Uri response = getIntent().getData();
		final BrowserOAuthSession session = new BrowserOAuthSession(this);
		if(session.recognizes(response, RedditOAuth.getConfiguredClientId())) {
			startActivity(new Intent(this, OAuthLoginActivity.class)
					.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
					.putExtra(OAuthLoginActivity.RESPONSE, response.toString()));
		} else {
			Toast.makeText(this, R.string.oauth_browser_invalid, Toast.LENGTH_LONG).show();
		}
		finish();
	}
}
