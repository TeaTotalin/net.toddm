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

import java.util.HashMap;
import java.util.Map;

/**
 * A simple implementation of the {@link ConfigurationProvider} interface that exposes a set of default configuration values 
 * used by the comm framework when a configuration provider is not supplied. One reason I do this here rather then relying 
 * entirely on in-line defaults (which still need to exist) is to centralize where these default values live.
 * <p>
 * @author Todd S. Murchison
 */
public class DefaultConfigurationProvider extends MapConfigurationProvider {

	//***************** Supported config keys
	/** The value mapped to this key defines the maximum number of unique redirects a request can result in before giving up. */
	public static final String KeyRedirectLimit = "redirect_limit";

	/** The value mapped to this key defines the maximum number of simultaneous requests that the comm framework will attempt to process at the same time. */
	public static final String KeyMaxSimultaneousRequests = "max_simultaneous_requests";

	/** The value mapped to this key defines the timeout value when waiting for connections to be established. */
	public static final String KeyConnectTimeoutMilliseconds = "connect_timeout_milliseconds";

	/** The value mapped to this key defines the timeout value when waiting for read responses. */
	public static final String KeyReadTimeoutMilliseconds = "read_timeout_milliseconds";

	/** If set to <b>true</b> invalid SSL certificates are accepted. In general this should never be done accept for development and testing purposes. */
	public static final String KeyDisableSSLCertChecking = "disable_ssl_cert_checking";

	/**
	 * The Comm Framework provides support for request redirection based on response (301, 302, 303). Depending on Java version, platform, etc. 
	 * implementers may prefer to make use of Java's built in redirection support as provided by the java.net classes. If this config value is 
	 * set to <b>false</b> (the default) then redirection is provided by the Comm Framework, otherwise redirection is provided by java.net.
	 */
	public static final String KeyUseBuiltInHttpURLConnectionRedirectionSupport = "use_built_in_http_url_connection_redirection_support";
	//***************** Supported config keys

	private static final Map<String, Object> _DefaultConfig = new HashMap<String, Object>();
	static {
		_DefaultConfig.put(KeyRedirectLimit, 3);
		_DefaultConfig.put(KeyMaxSimultaneousRequests, 2);
		_DefaultConfig.put(KeyConnectTimeoutMilliseconds, 30000);
		_DefaultConfig.put(KeyReadTimeoutMilliseconds, 30000);
		_DefaultConfig.put(KeyDisableSSLCertChecking, false);
		_DefaultConfig.put(KeyUseBuiltInHttpURLConnectionRedirectionSupport, false);
	}

	public DefaultConfigurationProvider() {
		super(_DefaultConfig);
	}

}
