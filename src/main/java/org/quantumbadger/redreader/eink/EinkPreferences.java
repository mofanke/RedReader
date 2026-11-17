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
import org.quantumbadger.redreader.common.General;

/** 墨水屏模式、实体键方向与文字重叠设置。 */
public final class EinkPreferences {

	private EinkPreferences() {}

	public static boolean enabled(final Context context) {
		return General.getSharedPrefs(context).getBoolean("eink_enabled", true);
	}

	public static boolean reverse(final Context context) {
		return General.getSharedPrefs(context).getBoolean("eink_reverse_volume", false);
	}

	public static int overlap(final Context context) {
		final String value = General.getSharedPrefs(context).getString("eink_overlap", "2");
		try {
			return Math.max(1, Math.min(5, Integer.parseInt(value)));
		} catch(final NumberFormatException ignore) {
			return 2;
		}
	}
}
