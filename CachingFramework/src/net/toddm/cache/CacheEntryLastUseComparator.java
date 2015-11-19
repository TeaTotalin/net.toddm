// ***************************************************************************
// *  Copyright 2015 Todd S. Murchison
// *
// *  Licensed under the Apache License, Version 2.0 (the "License");
// *  you may not use this file except in compliance with the License.
// *  You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// *  Unless required by applicable law or agreed to in writing, software
// *  distributed under the License is distributed on an "AS IS" BASIS,
// *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// *  See the License for the specific language governing permissions and
// *  limitations under the License.
// ***************************************************************************
package net.toddm.cache;

import java.util.Comparator;

/**
 * A simple implementation of {@link Comparator} for {@link CacheEntry} that allows for 
 * sorting cache entries first by their last use time and second by their last modified time.
 * This is suitable for basic LRU implementation.
 * <p>
 * @author Todd S. Murchison
 */
public class CacheEntryLastUseComparator implements Comparator<CacheEntry> {

	@Override
	public int compare(CacheEntry antryA, CacheEntry entryB) {
		int result = (int) (entryB.getTimestampUsed() - antryA.getTimestampUsed());
		if(result == 0) {
			result = (int) (entryB.getTimestampModified() - antryA.getTimestampModified());
		}
		return(result);
	}

}
