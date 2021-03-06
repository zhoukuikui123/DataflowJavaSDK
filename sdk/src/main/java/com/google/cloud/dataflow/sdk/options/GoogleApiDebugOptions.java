/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.dataflow.sdk.options;

import com.google.api.client.googleapis.services.AbstractGoogleClient;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.googleapis.services.GoogleClientRequestInitializer;
import com.google.common.base.Preconditions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * These options configure debug settings for Google API clients created within the Dataflow SDK.
 */
public interface GoogleApiDebugOptions extends PipelineOptions {
  /**
   * This option enables tracing of API calls to Google services used within the Dataflow SDK.
   * A tracing token must be requested from Google to be able to use this option.
   * An invalid tracing token will result in 400 errors from Google when the API is invoked.
   */
  @Description("This option enables tracing of API calls to Google services used within the "
      + "Dataflow SDK. Values are expected in the format \"ApiName#TracingToken\" where the "
      + "ApiName represents the request classes canonical name. The TracingToken must be requested "
      + "from Google to be able to use this option. An invalid tracing token will result in HTTP "
      + "400 errors from Google when the API is invoked. Note, that by enabling this option, the "
      + "contents of the requests to and from Google Cloud services will be made available to "
      + "Google. For example, by specifiying \"Dataflow#TracingToken\", all calls to the Dataflow "
      + "service will be made available to Google.")
  GoogleApiTracer[] getGoogleApiTrace();
  void setGoogleApiTrace(GoogleApiTracer... commands);

  /**
   * A {@link GoogleClientRequestInitializer} which adds the 'trace' token to Google API calls.
   */
  public static class GoogleApiTracer implements GoogleClientRequestInitializer {
    private static final Pattern COMMAND_LINE_PATTERN = Pattern.compile("([^#]*)#(.*)");
    /**
     * Creates a {@link GoogleApiTracer} which sets the trace {@code token} on all
     * calls which match the given client type.
     */
    public static GoogleApiTracer create(AbstractGoogleClient client, String token) {
      return new GoogleApiTracer(client.getClass().getCanonicalName(), token);
    }

    /**
     * Creates a {@link GoogleApiTracer} which sets the trace {@code token} on all
     * calls which match for the given request type.
     */
    public static GoogleApiTracer create(AbstractGoogleClientRequest<?> request, String token) {
      return new GoogleApiTracer(request.getClass().getCanonicalName(), token);
    }

    /**
     * Creates a {@link GoogleClientRequestInitializer} which adds the trace token
     * based upon the passed in value.
     * <p>
     * The {@code value} represents a string containing {@code ApiName#TracingToken}.
     * The {@code ApiName} is used to match against the request classes
     * {@link Class#getCanonicalName() canonical name} for which to add the {@code TracingToken} to.
     * For example, to match:
     * <ul>
     *   <li>all Google API calls: {@code #TracingToken}
     *   <li>all Dataflow API calls: {@code Dataflow#TracingToken}
     *   <li>all Dataflow V1B3 API calls: {@code Dataflow.V1b3#TracingToken}
     *   <li>all Dataflow V1B3 Jobs API calls: {@code Dataflow.V1b3.Projects.Jobs#TracingToken}
     *   <li>all Dataflow V1B3 Jobs Get calls: {@code Dataflow.V1b3.Projects.Jobs.Get#TracingToken}
     *   <li>all Job creation calls in any version: {@code Jobs.Create#TracingToken}
     * </ul>
     */
    @JsonCreator
    public static GoogleApiTracer create(String value) {
      Matcher matcher = COMMAND_LINE_PATTERN.matcher(value);
      Preconditions.checkArgument(matcher.find() && matcher.groupCount() == 2,
          "Unable to parse '%s', expected format 'ClientRequestName#Token'", value);
      return new GoogleApiTracer(matcher.group(1), matcher.group(2));
    }

    private final String clientRequestName;
    private final String token;

    private GoogleApiTracer(String clientRequestName, String token) {
      this.clientRequestName = clientRequestName;
      this.token = token;
    }

    @Override
    public void initialize(AbstractGoogleClientRequest<?> request) throws IOException {
      if (request.getClass().getCanonicalName().contains(clientRequestName)) {
        request.set("trace", token);
      }
    }

    @JsonValue
    @Override
    public String toString() {
      return clientRequestName + "#" + token;
    }
  }
}
