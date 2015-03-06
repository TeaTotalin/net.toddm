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
 * For maximum compatibility across Java versions and various platforms (such as Android, etc.) this simple 
 * class provides base 64 functionality without relying on any external libraries or newer Java features.
 * <p>
 * @author Todd S. Murchison
 */
public class Base64 {

	// Initialize base 64 encoding map data
    private static final char[] encodingMap;
    static {
    	encodingMap = new char[64];
        int i;
        for(i = 0; i < 26; i++) { encodingMap[i] = (char)('A' + i); }
        for(i = 26; i < 52; i++) { encodingMap[i] = (char)('a' + (i - 26)); }
        for(i = 52; i < 62; i++) { encodingMap[i] = (char)('0' + (i - 52)); }
        encodingMap[62] = '+';
        encodingMap[63] = '/';
    }

    private static char encodeByteAsChar(int i) {
        return(encodingMap[i & 0x3F]);
    }

    /** Base 64 encodes the given byte array and returns the resulting string. */
	public static String encode(byte[] input) {
		if((input == null) || (input.length <= 0)) { throw(new IllegalArgumentException("'input' can not be NULL or empty")); }

		char[] buffer = new char[((input.length + 2) / 3) * 4];
		int bufferIndex = 0;

		for(int i = 0; i < input.length; i += 3) {
			switch(input.length - i) {
				case 1:
					buffer[bufferIndex++] = encodeByteAsChar(input[i] >> 2);
					buffer[bufferIndex++] = encodeByteAsChar(((input[i]) & 0x3) << 4);
					buffer[bufferIndex++] = '=';
					buffer[bufferIndex++] = '=';
					break;
				case 2:
					buffer[bufferIndex++] = encodeByteAsChar(input[i] >> 2);
					buffer[bufferIndex++] = encodeByteAsChar(((input[i] & 0x3) << 4) | ((input[i + 1] >> 4) & 0xF));
					buffer[bufferIndex++] = encodeByteAsChar((input[i + 1] & 0xF) << 2);
					buffer[bufferIndex++] = '=';
					break;
				default:
					buffer[bufferIndex++] = encodeByteAsChar(input[i] >> 2);
					buffer[bufferIndex++] = encodeByteAsChar(((input[i] & 0x3) << 4) | ((input[i + 1] >> 4) & 0xF));
					buffer[bufferIndex++] = encodeByteAsChar(((input[i + 1] & 0xF) << 2) | ((input[i + 2] >> 6) & 0x3));
					buffer[bufferIndex++] = encodeByteAsChar(input[i + 2] & 0x3F);
					break;
			}
		}

		if(bufferIndex != buffer.length) {
			throw(new IllegalStateException("Base64 encoding failed"));
		}
		return(new String(buffer));
	}

}
