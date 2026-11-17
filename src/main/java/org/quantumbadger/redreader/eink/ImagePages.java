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

/** 整幅图片按行列覆盖，末段截断，并保留少量相邻重叠。 */
public final class ImagePages {
	public final int width;
	public final int height;
	public final int tileWidth;
	public final int tileHeight;
	public final int columns;
	public final int rows;
	public final float scale;
	private final int mStepX;
	private final int mStepY;

	public ImagePages(final int width, final int height, final int viewWidth,
			final int viewHeight, final boolean original, final int overlap) {
		if(width < 1 || height < 1 || viewWidth < 1 || viewHeight < 1) {
			throw new IllegalArgumentException("Image and viewport must have positive dimensions");
		}
		this.width = width;
		this.height = height;
		scale = original ? 1f : Math.min(1f, (float)viewWidth / width);
		tileWidth = original ? Math.min(width, viewWidth) : width;
		tileHeight = Math.min(height, Math.max(1, (int)Math.floor(viewHeight / scale)));
		final int pixels = Math.max(0, (int)Math.ceil(overlap / scale));
		mStepX = Math.max(1, tileWidth - Math.min(pixels, tileWidth / 4));
		mStepY = Math.max(1, tileHeight - Math.min(pixels, tileHeight / 4));
		columns = 1 + (int)(((long)width - tileWidth + mStepX - 1) / mStepX);
		rows = 1 + (int)(((long)height - tileHeight + mStepY - 1) / mStepY);
	}

	public int count() {
		final long count = (long)columns * rows;
		if(count > Integer.MAX_VALUE) {
			throw new ArithmeticException("Too many image regions");
		}
		return (int)count;
	}

	public int[] region(final int index) {
		if(index < 0 || index >= count()) {
			throw new IndexOutOfBoundsException();
		}
		final int x = (int)((long)(index % columns) * mStepX);
		final int y = (int)((long)(index / columns) * mStepY);
		return new int[]{x, y, (int)Math.min(width, (long)x + tileWidth),
				(int)Math.min(height, (long)y + tileHeight)};
	}

	public int sample() {
		int value = 1;
		while(value <= 1024 && value * 2 <= 1f / scale) {
			value *= 2;
		}
		while((long)(tileWidth / value) * (tileHeight / value) > 4_000_000) {
			value *= 2;
		}
		return value;
	}

	public int indexAt(final int x, final int y) {
		return Math.max(0, Math.min(rows - 1, y / mStepY)) * columns
				+ Math.max(0, Math.min(columns - 1, x / mStepX));
	}
}
