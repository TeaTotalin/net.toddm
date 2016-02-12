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

/**
 * This interface is implemented by parties that are providing dependent work via {@link Work#setDependentWork(Work, DependentWorkListener)}.
 * <p>
 * @author Todd S. Murchison
 */
public interface DependentWorkListener {

	/**
	 * This is called when processing for the relevant dependent {@link Work} completes.  The dependent request 
	 * may have finished successfully, failed, been canceled, etc., but no additional work will be done for it.
	 * <p>
	 * @param dependentWork The dependent {@link Work} instances that has finished processing.
	 * @param currentWork The work that is dependent on dependentWork and that will be processed next if this implementation returns <b>true</b>.
	 * @return Implementations should, based on the results of the dependent work, return <b>true</b> if the 
	 * current work should continue or <b>false</b> if the current work should be canceled.
	 */
	public boolean onDependentWorkCompleted(Work dependentWork, Work currentWork);

}
