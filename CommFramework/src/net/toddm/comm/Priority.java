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

/** A class that represents a priority for a unit of {@link Work} being managed by an instance of {@link CommManager}. */
public class Priority {

	/** An enumeration of priority levels that calling code can claim for their {@Work} instances */
	public enum StartingPriority {

		// TODO: Consider the idea of a "NEVER PROMOTES" starting priority.
		// This level would be lower priority than "LOW" and would never get promoted. It would be used when client code is declaring that, 
		// in theory, it is OK if the work NEVER happens and if it does happen it's done when there is simply nothing else to work on. The 
		// complication here is that this implies meaning that must be understood by the PriorityManagmentProvider implementation. Probably 
		// the way to do this would simply be to create a "LOWEST" starting priority and create a well documented, well named 
		// PriorityManagmentProvider implementation that makes it clear the "LOWEST" level is never promoted.

		/** Work with this priority level will be done <b>after</b> any other pending work. */
		LOW(10),
		/** In general, unless you are sure that your work merits special priority, this is the default priority to use when submitting work. */
		MEDIUM(7),
		/** Work with this priority level will be done <b>before</b> any other pending work. */
		HIGH(3);

		private final int _priority;
		private StartingPriority(int priorityValue) { this._priority = priorityValue; }

		/** Returns the integer value of the this Priority instance */
		public int getPriorityValue() { return(this._priority); }
	};

	private final Work _work;
	private final StartingPriority _startingPriority;
	private final long _createdTimestamp;
	protected int _priority;
	protected long _lastPromotionTimestamp;

	/**
	 * Constructs an instance of {@link Priority}
	 * <p>
	 * @param work The instance of {@link Work} that this {@link Priority} instance represents the priority of.
	 * @param startingPriority The priority to start the related work at.
	 */
	public Priority(Work work, StartingPriority startingPriority) {
		if(work == null) { throw(new IllegalArgumentException("'work' can not be NULL")); }
		if(startingPriority == null) { throw(new IllegalArgumentException("'startingPriority' can not be NULL")); }
		this._work = work;
		this._startingPriority = startingPriority;
		this._priority = startingPriority.getPriorityValue();
		this._createdTimestamp = System.currentTimeMillis();
		this._lastPromotionTimestamp = this._createdTimestamp;
	}

	/** Returns the instance of {@link Work} that this {@link Priority} instance represents the priority of. */
	public Work getWork() { return(this._work); }

	/** Returns the {@link StartingPriority} that this instance started with. */
	public StartingPriority getStartingValue() { return(this._startingPriority); }

	/** Returns the current priority value of this instance. */
	public int getValue() { return(this._priority); }

	/** Returns the creation timestamp of this {@link Priority} instance in epoch milliseconds. */
	protected long getCreatedTimestamp() { return(this._createdTimestamp); }

	/** Returns the timestamp of the last priority promotion of this {@link Priority} instance in epoch milliseconds. */
	protected long getLastPromotionTimestamp() { return(this._lastPromotionTimestamp); }

}
