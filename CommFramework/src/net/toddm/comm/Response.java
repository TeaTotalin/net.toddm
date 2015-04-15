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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Todd S. Murchison
 */
public class Response implements Serializable {
	private static final long serialVersionUID = -6722104702458701972L;

	private static final Logger _Logger = LoggerFactory.getLogger(Response.class.getSimpleName());

	/** {@serial} */
	private byte[] _responseBytes;
	/** {@serial} */
	private int _responseCode;
	/** {@serial} */
	private int _requestId;
	/** {@serial} */
	private int _responseTime = -1;
	/** {@serial} */
	private Map<String, List<String>> _headers;

	/** Constructor for the implementation of {@link Serializable}, should not be used otherwise. */
	public Response() {}

	protected Response(byte[] responseBytes, Map<String, List<String>> headers, int responseCode, int requestId, int responseTime) {
		this._responseBytes = responseBytes;
		if(headers != null) {

			// Ensure that we use a Map implementation that can be serialized no matter what type of Map is passed in
			this._headers = new HashMap<String, List<String>>(headers);
		} else {
			this._headers = new HashMap<String, List<String>>();
		}
		this._responseCode = responseCode;
		this._requestId = requestId;
		this._responseTime = responseTime;
	}
	
	public byte[] getResponseBytes() {
		return(this._responseBytes);
	}

	public String getResponseBody() {
		String result = null;
		try {
			result = new String(this._responseBytes, "UTF-8");
		} catch(UnsupportedEncodingException uee) {
			_Logger.debug("Response encoding as string failed");  // No-op OK
		}
		return(result);
	}

	public Integer getResponseCode() {
		return(this._responseCode);
	}

	/**
	 * Returns <b>true</b> if this {@link Response} instance has an HTTP response 
	 * code that we consider to indicate "success". Currently this either 200 or 201.
	 */
	public boolean isSuccessful() {
		return((this._responseCode == 200) || (this._responseCode == 201));
	}

	/** Returns the unique ID of the {@link Request} that generated this {@link Response}. */
	public Integer getRequestId() {
		return(this._requestId);
	}

	/** The HTTP header values from the response. */
	public Map<String, List<String>> getHeaders() { return(this._headers); }

	/** For the implementation of {@link Serializable}. */
	private void writeObject(java.io.ObjectOutputStream out) throws IOException {

		out.writeInt(this._responseCode);
		out.writeInt(this._requestId);
		out.writeInt(this._responseTime);
		out.writeObject(this._headers);

		// Add the byte array last if there is one
		if(this._responseBytes != null) {
			out.write(this._responseBytes);
		}
	}

	/** For the implementation of {@link Serializable}. */
	@SuppressWarnings("unchecked")
	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {

		this._responseCode = in.readInt();
		this._requestId = in.readInt();
		this._responseTime = in.readInt();
		this._headers = (Map<String, List<String>>)in.readObject();

		// Any remaining bytes are the response body (that's why we add it last)
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int bytesRead = 0;
		while((bytesRead = in.read(buffer)) != -1) {
			outStream.write(buffer, 0, bytesRead);
		}
		this._responseBytes = null;
		if(outStream.size() > 0) {
			this._responseBytes = outStream.toByteArray();
		}
	}

}
