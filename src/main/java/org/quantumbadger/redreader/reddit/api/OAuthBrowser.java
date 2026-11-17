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

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import java.util.List;

/** 只在真正的网页浏览器中授权，允许浏览器将 OAuth 回跳交回应用。 */
public final class OAuthBrowser {
	private OAuthBrowser() {}

	public static Intent intent(final Activity activity, final Uri uri, final boolean tor) {
		final Intent generic = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org/"))
				.addCategory(Intent.CATEGORY_BROWSABLE);
		final PackageManager manager = activity.getPackageManager();
		final List<ResolveInfo> browsers = manager.queryIntentActivities(
				generic, PackageManager.MATCH_DEFAULT_ONLY);
		final ResolveInfo preferred = manager.resolveActivity(generic,
				PackageManager.MATCH_DEFAULT_ONLY);
		String selected = null;
		for(final ResolveInfo browser : browsers) {
			if(browser.activityInfo == null || !browser.activityInfo.exported
					|| activity.getPackageName().equals(browser.activityInfo.packageName)) {
				continue;
			}
			final String name = browser.activityInfo.packageName;
			if(tor && !name.equals("org.torproject.torbrowser")
					&& !name.equals("org.torproject.torbrowser_alpha")) {
				continue;
			}
			if(selected == null || (preferred != null && preferred.activityInfo != null
					&& name.equals(preferred.activityInfo.packageName))) {
				selected = name;
			}
		}
		if(selected == null) {
			return null;
		}
		final Bundle extras = new Bundle();
		extras.putBinder("android.support.customtabs.extra.SESSION", null);
		return new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
				.setPackage(selected).putExtras(extras)
				.putExtra("android.support.customtabs.extra.SEND_TO_EXTERNAL_HANDLER", false)
				.putExtra("androidx.browser.customtabs.extra.SHARE_STATE", 2);
	}
}
