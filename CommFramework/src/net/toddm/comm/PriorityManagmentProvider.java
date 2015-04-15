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
package net.toddm.comm;

import java.util.Comparator;

/**
 * Priority management providers implement this interface in order to control priority queue functionality such as priority promotion and priority queue sorting.
 * <p>
 * @author Todd S. Murchison
 */
public interface PriorityManagmentProvider {

	/**
	 * Decides if the given instance of {@link Priority} should be adjusted and makes the adjustment if it should.<br>
	 * <b>Note</b>: {@link CommManager} will call this method each time it is deciding if work should begin, implement accordingly.
	 */
	public void promotePriority(Priority priority);

	/**
	 * Returns a {@link Comparator} that will be used to control priority order and thus effect the order in which work is attempted.
	 * <b>Note</b>: {@link CommManager} will call this method each time it is deciding if work should begin, implement accordingly.
	 */
	public Comparator<Priority> getPriorityComparator();

}
