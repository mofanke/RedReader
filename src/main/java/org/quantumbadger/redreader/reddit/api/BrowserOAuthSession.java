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

package org.quantumbadger.redreader.reddit.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;
import java.security.SecureRandom;
import java.util.Objects;

/** 保存一次浏览器授权事务，验证回跳地址、有效期与随机 state。 */
public final class BrowserOAuthSession {
	public static final String STORE = "oauth_browser_session";
	private static final long LIFETIME = 30L * 60 * 1000;
	private final SharedPreferences mStore;

	public enum Result {
		SUCCESS, DENIED, INVALID
	}

	public BrowserOAuthSession(final Context context) {
		mStore = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
	}

	public String begin(final String clientId) {
		final byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		final String state = Base64.encodeToString(bytes,
				Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
		mStore.edit().clear().putString("state", state).putString("client", clientId)
				.putLong("started", System.currentTimeMillis()).apply();
		return state;
	}

	public static boolean isRedirect(final Uri uri) {
		return uri != null && "redreader".equals(uri.getScheme())
				&& "rr_oauth_redir".equals(uri.getHost()) && uri.getPort() == -1
				&& uri.getUserInfo() == null && uri.getFragment() == null
				&& (uri.getPath() == null || uri.getPath().isEmpty());
	}

	public boolean recognizes(final Uri uri, final String clientId) {
		if(!isRedirect(uri) || uri.getQueryParameters("state").size() != 1) {
			return false;
		}
		final String expected = mStore.getString("state", null);
		final long age = System.currentTimeMillis() - mStore.getLong("started", 0);
		return expected != null && expected.equals(uri.getQueryParameter("state"))
				&& clientId != null && Objects.equals(clientId, mStore.getString("client", null))
				&& age >= 0 && age <= LIFETIME;
	}

	public Result consume(final Uri uri, final String clientId) {
		if(!recognizes(uri, clientId)) {
			return Result.INVALID;
		}
		final boolean code = uri.getQueryParameters("code").size() == 1
				&& !Objects.requireNonNull(uri.getQueryParameter("code")).isEmpty();
		final boolean error = uri.getQueryParameters("error").size() == 1
				&& !Objects.requireNonNull(uri.getQueryParameter("error")).isEmpty();
		if(code == error || (code && !uri.getQueryParameters("error").isEmpty())
				|| (error && !uri.getQueryParameters("code").isEmpty())) {
			return Result.INVALID;
		}
		clear();
		return code ? Result.SUCCESS : Result.DENIED;
	}

	public void clear() {
		mStore.edit().clear().apply();
	}
}
