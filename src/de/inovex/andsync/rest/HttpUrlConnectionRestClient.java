/*
 * Copyright 2012 Tim Roes <tim.roes@inovex.de>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.inovex.andsync.rest;

import de.inovex.andsync.util.Joiner;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import static de.inovex.andsync.Constants.*;

/**
 *
 * @author Tim Roes <tim.roes@inovex.de>
 */
class HttpUrlConnectionRestClient extends RestClient {	
		
	private enum Method {
		
		GET, POST, PUT, DELETE;
		
		/**
		 * Returns the {@link String} representation of the given method, the way it has to be
		 * used in the HTTP header. 
		 */
		public String method() {
			return name();
		}
		
	}

	private URL baseUrl;
	
	HttpUrlConnectionRestClient(URL baseUrl) {
		this.baseUrl = baseUrl;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public RestResponse get(String... path) throws RestException {
		return call(Method.GET, null, path);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RestResponse delete(String... path) throws RestException {
		return call(Method.DELETE, null, path);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RestResponse put(byte[] data, String... path) throws RestException {
		return call(Method.PUT, data, path);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RestResponse post(byte[] data, String... path) throws RestException {
		return call(Method.POST, data, path);
	}
	
	private RestResponse call(Method method, byte[] data, String... path) throws RestException {
		
		try {
			
			for(int i = 0; i < path.length; i++) {
				path[i] = URLEncoder.encode(path[i], UTF8);
			}
		
			String p = Joiner.join("/", path);
			
			URL callUrl = new URL(baseUrl, p);
			
			HttpURLConnection http = openConnection(callUrl);
			
			http.setRequestMethod(method.method());
			
			writeHttpOutput(http, data);
			
			int responseCode = -1;
			try {
				responseCode = http.getResponseCode();
			} catch(IOException ex) {
				// Android might 
				responseCode = http.getResponseCode();
			}
			
			InputStream in;
			
			if(responseCode >= 200 && responseCode < 300) {
				in = http.getInputStream();
			} else {
				in = http.getErrorStream();
			}
			
			byte[] content = toByteArray(in);
			
			return new RestResponse(responseCode, content);						

		} catch(IOException ex) {
			throw new RestException(ex);
		}

	}
	
	private HttpURLConnection openConnection(URL url) throws IOException {
		
		URLConnection conn = url.openConnection();
		
		if(!(conn instanceof HttpURLConnection)) {
			throw new IOException("Could not open HTTP connection on the given URL.");
		}
		
		return (HttpURLConnection)conn;
		
	}
	
	private void writeHttpOutput(HttpURLConnection conn, byte[] output) throws IOException {
		
		if(output == null) return;
		
		conn.setDoOutput(true);
		OutputStream out = conn.getOutputStream();
		out.write(output);
		out.flush();
		out.close();
		
	}
	
	private byte[] toByteArray(final InputStream stream) throws IOException {
		
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		
		int read;
		byte[] buffer = new byte[1024 * 4];
		
		while((read = stream.read(buffer, 0, buffer.length)) != -1) {
			output.write(buffer, 0, read);
		}
		
		output.flush();		
		
		return output.toByteArray();
		
	}
	
}
