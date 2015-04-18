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
package net.toddm.comm.tests;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.toddm.comm.Base64;
import junit.framework.TestCase;

public class TestBase64 extends TestCase {

	private static Logger _Logger = LoggerFactory.getLogger(TestBase64.class.getSimpleName());
	
	public void testEncodeSimple() throws Exception {

		byte[] input = new String("the quick brown fox jumped over the lazy dog").getBytes("UTF-8");
		String output = Base64.encode(input);
		_Logger.debug("Base64.encode produced: '" + output + "'");

		// This expected value was arrived at using both the Java 8 DatatypeConverter.printBase64Binary() method
		// as well as an on-line base 64 encoding utility (http://www.opinionatedgeek.com/dotnet/tools/base64encode/).
		//
		// Expected:	dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wZWQgb3ZlciB0aGUgbGF6eSBkb2c=
		assertNotNull(output);
		assertTrue(output.length() > 0);
		assertEquals("dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wZWQgb3ZlciB0aGUgbGF6eSBkb2c=", output);
	}

	public void testEncodeComplex() throws Exception {

		byte[] input = new String("┤╥,65♀635L2☻~32┐2◙⌠1j32┐53K_").getBytes("UTF-8");
		String output = Base64.encode(input);
		_Logger.debug("Base64.encode produced: '" + output + "'");

		// This expected value was arrived at using both the Java 8 DatatypeConverter.printBase64Binary() method
		// as well as an on-line base 64 encoding utility (http://www.opinionatedgeek.com/dotnet/tools/base64encode/).
		//
		// Expected:	4pSk4pWlLDY14pmANjM1TDLimLt+MzLilJAy4peZ4oygMWozMuKUkDUzS18=
		assertNotNull(output);
		assertTrue(output.length() > 0);
		assertEquals("4pSk4pWlLDY14pmANjM1TDLimLt+MzLilJAy4peZ4oygMWozMuKUkDUzS18=", output);
	}

}
