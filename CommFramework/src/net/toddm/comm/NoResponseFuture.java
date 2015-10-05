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

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * This is a simple no-op {@link FutureTask} implementation that simply returns a <b>null</b> {@link Response}.
 * We provide this wrapping so that no special case code is ever required. Client code can still be written to, for
 * example, block on future get() wait handles.  In this case no result <b>null</b> is simply returned immediately.
 * <p>
 * @author Todd S. Murchison
 */
public class NoResponseFuture extends FutureTask<Response> {

	/** A {@link Callable} implementation that always returns NULL. */
	private static Callable<Response> _ReturnNullResponseCallable = new Callable<Response>() {
		@Override
		public Response call() throws Exception { return(null); }
	};

	/** Constructors a {@link FutureTask} instance that always immediately returns <b>null</b>. */
	protected NoResponseFuture() {
		super(_ReturnNullResponseCallable);
	}

	/** Immediately returns <b>null</b>. */
	@Override
	public Response get() {
		return(null);
	}

	/** Immediately returns <b>null</b>. */
	@Override
	public Response get(long timeout, TimeUnit unit) {
		return(null);
	}

	/** Always returns FALSE. */
	@Override
	public boolean isCancelled() { return(false); }

	/** Always returns TRUE. */
	@Override
	public boolean isDone() { return(true); }

	/** Does nothing. */
	@Override
	public void run() { }

}
