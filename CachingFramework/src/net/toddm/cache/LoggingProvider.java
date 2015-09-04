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

/** Client code provides an implementation of this interface if it wants to receive logging from the framework. */
public interface LoggingProvider {

	void info(String msg, Object... msgArgs);

	void debug(String msg, Object... msgArgs);

	void error(String msg, Object... msgArgs);

	void error(Throwable t, String msg, Object... msgArgs);

}
