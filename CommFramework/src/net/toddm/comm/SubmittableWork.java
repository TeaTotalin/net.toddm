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

import net.toddm.cache.CachePriority;

/**
 * An interface implemented by the Comm Framework to publicly express units of work that can be submitted for processing.
 * <p>
 * @author Todd S. Murchison
 */
public interface SubmittableWork {

	/**
	 * Returns the ID of this {@link SubmittableWork} instance. The ID of the underlying 
	 * {@link Request} is used. See {@link Request#getId()} for details.
	 */
	public int getId();

	/** Returns the {@link Request} instance associated with this {@link SubmittableWork} instance. */
	public Request getRequest();

	/** Returns the request {@link Priority} of this {@link SubmittableWork} instance. */
	public Priority getRequestPriority();

	/** Returns the caching {@link CachePriority} of this {@link SubmittableWork} instance, or <b>null<b> if it had no priority set. */
	public CachePriority getCachingPriority();

	/** Returns the caching {@link CacheBehavior} of this {@link SubmittableWork} instance, or <b>null<b> if it had no behavior set. */
	public CacheBehavior getCachingBehavior();

	/**
	 * Causes this {@link SubmittableWork} to be dependent on the provided Work. When processing this Work the {@link CommManager} will 
	 * ensure that the given Work is processed first.  This may include pausing the current Work, starting the dependent Work 
	 * if it has not yet been started, etc.
	 * 
	 * @param dependentWork An instance of {@link SubmittableWork} that must finish processing before this instance is processed.
	 * @param dependentWorkListener [OPTIONAL] Can be NULL.  If provided, this callback is made with the results of the 
	 * dependent Work before the current work is processed.
	 */
	public void setDependentWork(SubmittableWork dependentWork, DependentWorkListener dependentWorkListener);

}
