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
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 只在真正的网页浏览器中授权，允许浏览器将 OAuth 回跳交回应用。 */
public final class OAuthBrowser {
	private OAuthBrowser() {}

	public static Intent intent(final Activity activity, final Uri uri, final boolean tor) {
		final List<Intent> browsers = intents(activity, uri, tor);
		return browsers.isEmpty() ? null : browsers.get(0);
	}

	public static List<Intent> intents(final Activity activity, final Uri uri, final boolean tor) {
		final PackageManager manager = activity.getPackageManager();
		final Map<String, Intent> candidates = new LinkedHashMap<>();
		for(final String scheme : new String[]{"https", "http"}) {
			final Intent generic = new Intent(Intent.ACTION_VIEW,
					Uri.parse(scheme + "://example.org/")).addCategory(Intent.CATEGORY_BROWSABLE);
			for(final ResolveInfo browser : manager.queryIntentActivities(generic, 0)) {
				final ActivityInfo info = browser.activityInfo;
				if(usable(activity, info, tor) && !candidates.containsKey(info.packageName)) {
					candidates.put(info.packageName, new Intent(Intent.ACTION_VIEW, uri)
							.addCategory(Intent.CATEGORY_BROWSABLE).setPackage(info.packageName)
							.setComponent(new ComponentName(info.packageName, info.name)));
				}
			}
		}
		final Intent browserCategory = new Intent(Intent.ACTION_MAIN)
				.addCategory(Intent.CATEGORY_APP_BROWSER);
		for(final ResolveInfo browser : manager.queryIntentActivities(browserCategory, 0)) {
			final ActivityInfo info = browser.activityInfo;
			if(usable(activity, info, tor) && !candidates.containsKey(info.packageName)) {
				candidates.put(info.packageName, new Intent(Intent.ACTION_VIEW, uri)
						.addCategory(Intent.CATEGORY_BROWSABLE).setPackage(info.packageName));
			}
		}
		final Intent generic = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org/"))
				.addCategory(Intent.CATEGORY_BROWSABLE);
		final ResolveInfo preferred = manager.resolveActivity(generic,
				PackageManager.MATCH_DEFAULT_ONLY);
		final List<Intent> result = new ArrayList<>();
		if(preferred != null && preferred.activityInfo != null) {
			final Intent first = candidates.remove(preferred.activityInfo.packageName);
			if(first != null) {
				result.add(first);
			}
		}
		result.addAll(candidates.values());
		return result;
	}

	private static boolean usable(final Activity activity, final ActivityInfo info,
			final boolean tor) {
		return info != null && info.exported && info.enabled
				&& !activity.getPackageName().equals(info.packageName)
				&& (!tor || "org.torproject.torbrowser".equals(info.packageName)
				|| "org.torproject.torbrowser_alpha".equals(info.packageName));
	}
}
