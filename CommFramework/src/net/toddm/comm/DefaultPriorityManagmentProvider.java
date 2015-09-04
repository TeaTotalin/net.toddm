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

import net.toddm.cache.LoggingProvider;

/**
 * A simple implementation of {@link PriorityProvider} that guards against starvation with simple timestamps based priority promotion.
 * <p>
 * @author Todd S. Murchison
 */
public class DefaultPriorityManagmentProvider implements PriorityManagementProvider {

	// TODO: CONFIG: This Promotion Interval In Milliseconds value should probably come from configuration
	private static long _PromotionIntervalInMilliseconds = 60000;  // One minute
	private final LoggingProvider _logger;

	/**
	 * Returns an instance of {@link DefaultPriorityManagmentProvider}.
	 * 
	 * @param loggingProvider <b>OPTIONAL</b> If NULL no logging callbacks are made otherwise the provided implementation will get log messages.
	 */
	public DefaultPriorityManagmentProvider(LoggingProvider loggingProvider) {
		this._logger = loggingProvider;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * This implementation does simple priority promotion based on age to help alleviate queue starvation.
	 */
	@Override
	public void promotePriority(Priority priority) {

		// If we can't promote any further there's no work to do
		if(priority.getValue() <= 1) { return; }

		// If it has been long enough since our last priority promotion we should promote
		boolean promote = ((System.currentTimeMillis() - priority.getLastPromotionTimestamp()) >= _PromotionIntervalInMilliseconds);
		if(promote) {

			// Promote priority and update timestamp
			if(this._logger != null) { this._logger.debug("promotePriority() PRE [request:%1$d priority:%2$d]", priority.getWork().getId(), priority.getValue()); }
			priority._lastPromotionTimestamp = System.currentTimeMillis();
			priority._priority--;
			if(this._logger != null) { this._logger.debug("promotePriority() POST [request:%1$d priority:%2$d]", priority.getWork().getId(), priority.getValue()); }
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * This implementation sorts on priority values and then on creation timestamp for equal priorities.
	 */
	@Override
	public Comparator<Priority> getPriorityComparator() { return(_PriorityComparator); }

	/** A {@link Comparator} implementation providing priority and creation time based comparison. */
	private static Comparator<Priority> _PriorityComparator = new Comparator<Priority>() {

		/**
		 * {@inheritDoc}
		 * <p>
		 * Returns a negative integer if the left instance of {@link Priority} (lhs) has a lower priority than the right instance (rhs), 
		 * a positive integer if lhs has a higher priority than rhs, or 0 if both instances of {@link Priority} have the same priority.
		 * <p>
		 * <strong>NOTE:</strong> In this context "priority" is algorithmically determined and does not directly reflect any one data member.
		 */
		@Override
		public int compare(Priority lhs, Priority rhs) {
			if(lhs == null) { throw(new IllegalArgumentException("'lhs' can not be NULL")); }
			if(rhs == null) { throw(new IllegalArgumentException("'rhs' can not be NULL")); }

			// Calculate order based on priority
			int order = 0;
			if(lhs._priority < rhs._priority) { order = -1; }
			else if(lhs._priority > rhs._priority) { order = 1; }

			// If the priorities are equal then base the order on creation timestamp
			if(order == 0) {
				if(lhs.getCreatedTimestamp() > rhs.getCreatedTimestamp()) { order = -1; }		// LHS is newer so is lower priority
				else if(lhs.getCreatedTimestamp() < rhs.getCreatedTimestamp()) { order = 1; }	// LHS is older so is higher priority
			}

			return(order);
		}

	};

}
