package net.sourceforge.guacacmole.net.hmac;

import junit.framework.TestCase;
import net.sourceforge.guacamole.GuacamoleException;
import net.sourceforge.guacamole.net.auth.Credentials;
import net.sourceforge.guacamole.net.hmac.HmacAuthenticationProvider;
import net.sourceforge.guacamole.net.hmac.TimeProviderInterface;
import net.sourceforge.guacamole.properties.GuacamoleProperties;
import net.sourceforge.guacamole.protocol.GuacamoleConfiguration;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static net.sourceforge.guacamole.net.hmac.HmacAuthenticationProvider.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HmacAuthenticationProviderTest extends TestCase {

    public void testSuccess() throws GuacamoleException {
        final String connectionId = "my-connection";
        HttpServletRequest request = getHttpServletRequest(connectionId);

        Credentials credentials = new Credentials();
        credentials.setRequest(request);

        TimeProviderInterface timeProvider = mock(TimeProviderInterface.class);
        when(timeProvider.currentTimeMillis()).thenReturn(1373563683000L);
        HmacAuthenticationProvider authProvider = new HmacAuthenticationProvider(timeProvider);

        Map<String, GuacamoleConfiguration> configs = authProvider.getAuthorizedConfigurations(credentials);

        assertNotNull(configs);
        assertEquals(1, configs.size());
        GuacamoleConfiguration config = configs.get(connectionId);
        assertNotNull(config);
        assertEquals("rdp", config.getProtocol());
    }

    public void testTimestampFresh() throws Exception {
        long ONE_HOUR = 60000L;
        final String connectionId = "my-connection";
        HttpServletRequest request = getHttpServletRequest(connectionId);

        Credentials credentials = new Credentials();
        credentials.setRequest(request);

        setGuacamoleProperty("timestamp-age-limit", String.valueOf(ONE_HOUR));

        TimeProviderInterface timeProvider = mock(TimeProviderInterface.class);
        when(timeProvider.currentTimeMillis()).thenReturn(1373563683000L + ONE_HOUR - 1l);
        HmacAuthenticationProvider authProvider = new HmacAuthenticationProvider(timeProvider);

        Map<String, GuacamoleConfiguration> configs = authProvider.getAuthorizedConfigurations(credentials);

        assertNotNull(configs);
        assertEquals(1, configs.size());
        GuacamoleConfiguration config = configs.get(connectionId);
        assertNotNull(config);
    }

    public void testTimestampStale() throws Exception {
        long ONE_HOUR = 60000L;
        final String connectionId = "my-connection";
        HttpServletRequest request = getHttpServletRequest(connectionId);

        Credentials credentials = new Credentials();
        credentials.setRequest(request);

        setGuacamoleProperty("timestamp-age-limit", String.valueOf(ONE_HOUR));

        TimeProviderInterface timeProvider = mock(TimeProviderInterface.class);
        when(timeProvider.currentTimeMillis()).thenReturn(1373563683000L + ONE_HOUR);
        HmacAuthenticationProvider authProvider = new HmacAuthenticationProvider(timeProvider);

        Map<String, GuacamoleConfiguration> configs = authProvider.getAuthorizedConfigurations(credentials);

        assertNull(configs);
    }

    private HttpServletRequest getHttpServletRequest(final String connectionId) {
        return mockRequest(new HashMap<String, String>() {{
            put(ID_PARAM,        connectionId);
            put("timestamp",     "1373563683000");
            put("guac.hostname", "10.2.3.4");
            put("guac.protocol", "rdp");
            put("guac.port",     "3389");
            // Test signature was generated with the following PHP snippet
            // base64_encode(hash_hmac('sha1', '1373563683000rdphostname10.2.3.4port3389', 'secret', true));
            put(SIGNATURE_PARAM, "6PHOr00TnhA10Ef9I4bLqeSXKYg=");
        }});
    }

    private static HttpServletRequest mockRequest(final Map<String, String> queryParams) {
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getParameter(anyString())).then(new Answer<Object>() {
            @Override
            public String answer(InvocationOnMock invocationOnMock) throws Throwable {
                String key = (String) invocationOnMock.getArguments()[0];
                return queryParams.get(key);
            }
        });

        // Note this is invalidating the servlet API, but I only use the keys so I don't care
        when(request.getParameterMap()).thenReturn(queryParams);

        return request;
    }

    private void setGuacamoleProperty(String propertyName, String propertyValue) throws NoSuchFieldException, IllegalAccessException {
        Field field = GuacamoleProperties.class.getDeclaredField("properties");
        field.setAccessible(true);
        Properties properties =  (Properties) field.get(GuacamoleProperties.class);
        properties.setProperty(propertyName, propertyValue);
    }

}
