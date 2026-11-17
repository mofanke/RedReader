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

import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** 实体键只调用一次屏幕按钮，并消费松开与重复事件。 */
public final class EinkKeys {
	private final Set<Integer> mHeld = new HashSet<>();

	public boolean dispatch(final int code, final int action, final boolean editing,
			final boolean available, final BooleanSupplier button) {
		if(code != 24 && code != 25 && code != 92 && code != 93) {
			return false;
		}
		if(action == 1 && mHeld.remove(code)) {
			return true;
		}
		if(mHeld.contains(code)) {
			return true;
		}
		if(editing || !available) {
			return false;
		}
		if(action == 2) {
			button.getAsBoolean();
			return true;
		}
		if(action == 1) {
			return true;
		}
		if(action == 0) {
			button.getAsBoolean();
			mHeld.add(code);
			return true;
		}
		return false;
	}

	public void clear() {
		mHeld.clear();
	}
}
