package ru.myx.srv.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.protocol.Protocol;

import ru.myx.ae1.know.AbstractServer;
import ru.myx.ae3.answer.Reply;
import ru.myx.ae3.answer.ReplyAnswer;
import ru.myx.ae3.base.Base;
import ru.myx.ae3.base.BaseObject;
import ru.myx.ae3.binary.Transfer;
import ru.myx.ae3.binary.TransferBuffer;
import ru.myx.ae3.binary.TransferCopier;
import ru.myx.ae3.exec.Exec;
import ru.myx.ae3.help.Convert;
import ru.myx.ae3.help.Text;
import ru.myx.ae3.report.Report;
import ru.myx.ae3.report.ReportReceiver;
import ru.myx.ae3.serve.ServeRequest;

final class ServerProxy extends AbstractServer {
	
	static {
		Protocol.registerProtocol("https", new Protocol("https", new EasySSLProtocolSocketFactory(), 443));
	}

	private final static ReplyAnswer applyHeaders(final HttpMethod method, final ReplyAnswer target) {
		
		final Header[] headers = method.getResponseHeaders();
		for (int i = headers.length - 1; i >= 0; --i) {
			final String key = headers[i].getName();
			if (!"Connection".equalsIgnoreCase(key) && !"Location".equalsIgnoreCase(key)) {
				target.addAttribute(key, headers[i].getValue());
			}
		}
		return target;
	}

	private final static void applyHeaders(final ServeRequest query, final HttpMethod target) {
		
		final BaseObject attributes = query.getAttributes();
		for (final Iterator<String> iterator = Base.keys(attributes); iterator.hasNext();) {
			final String key = iterator.next();
			if (!"Connection".equalsIgnoreCase(key) && !"Accept-Encoding".equalsIgnoreCase(key) && !"Host".equalsIgnoreCase(key) && !"Cookie".equalsIgnoreCase(key)) {
				target.addRequestHeader(key, Base.getString(attributes, key, ""));
			}
		}
	}

	private final HttpClient client;

	private final String address;

	private final int port;

	private ReportReceiver log;

	ServerProxy(final String id, final String address, final String port) {
		super(id, id, Exec.currentProcess());
		this.address = address == null || address.length() == 0 || "*".equals(address)
			? null
			: address;
		this.port = port == null || port.length() == 0 || "*".equals(port)
			? 0
			: Convert.Any.toInt(port, 0);
		this.client = new HttpClient();
		{
			final HostConfiguration config = new HostConfiguration();
			if (this.address != null) {
				if (this.port > 0) {
					config.setHost(this.address, this.port);
				} else {
					config.setHost(this.address);
				}
			}
			// !!! config.s clear fucking cookies or use new client on each
			// query
			this.client.setHostConfiguration(config);
		}
		{
			final MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
			this.client.setHttpConnectionManager(connectionManager);
		}
	}

	@Override
	public boolean absorb(final ServeRequest query) {
		
		if (this.log == null) {
			synchronized (this) {
				if (this.log == null) {
					/**
					 * FIXME: same this is already done for AbstractServer
					 * context!
					 */
					this.log = Report.createReceiver(this.getZoneId() + "-log");
				}
			}
		}
		final String uri = query.getResourceIdentifier();
		final String target = query.getProtocolName().toLowerCase() + "://" + (this.address == null
			? query.getTarget()
			: this.address) + uri;
		final String verb = query.getVerbOriginal();
		final HttpMethod method;
		{
			// if ("POST".equals( verb )) {
			// final PostMethod postMethod = new PostMethod( target );
			// method = postMethod;
			// final Map<String, Object> parameters = query.getParameters();
			// if ((parameters != null) && !parameters.isEmpty()) {
			// for (final String key : parameters.keySet()) {
			// postMethod.setParameter( key, String.valueOf( parameters.get( key
			// ) ) );
			// }
			// }
			// } else if ("HEAD".equals( verb )) {
			// final HeadMethod headMethod = new HeadMethod( target );
			// method = headMethod;
			// } else if ("OPTIONS".equals( verb )) {
			// final OptionsMethod headMethod = new OptionsMethod( target );
			// method = headMethod;
			// } else if ("GET".equals( verb )) {
			// final GetMethod getMethod = new GetMethod( target );
			// method = getMethod;
			// } else {
			method = new HttpMethodBase(target) {
				
				@Override
				public String getName() {
					
					return verb;
				}

				@Override
				protected boolean writeRequestBody(final HttpState state, final HttpConnection connection) throws IOException, HttpException {
					
					final TransferCopier buffer = query.toBinary().getBinary();
					if (buffer != null && buffer.length() > 0) {
						connection.write(buffer.nextDirectArray());
					}
					return true;
				}
			};
			// }
		}
		{
			final String queryString = query.getParameterString();
			if (queryString != null) {
				method.setQueryString(Text.encodeUri(queryString, "UTF-8"));
			}
		}
		{
			ServerProxy.applyHeaders(query, method);
			method.setFollowRedirects(false);
			method.setRequestHeader("x-host", query.getTarget());
			method.setRequestHeader("x-forwarded-host", query.getTarget());
		}
		final String baseUrl = query.getProtocolName().toLowerCase() + "://" + query.getTarget() + "/";
		this.log.event("PROXY", "enqueueing request", "target=" + query.getTarget() + ", url=" + target);
		try {
			final int code = this.client.executeMethod(method);
			if (code == 301 || code == 302) {
				final Header location = method.getResponseHeader("Location");
				if (location != null) {
					final String url = location.getValue();
					if (url.startsWith(baseUrl)) {
						query.getResponseTarget().apply(
								ServerProxy.applyHeaders(method, Reply.redirect("proxy(r1)", query, true, query.getResourcePrefix() + url.substring(baseUrl.length())))
										.setCode(code).setNoCaching());
						return true;
					}
					query.getResponseTarget().apply(ServerProxy.applyHeaders(method, Reply.redirect("proxy(r2)", query, true, url)).setCode(code).setNoCaching());
					return true;
				}
				query.getResponseTarget().apply(ServerProxy.applyHeaders(method, Reply.redirect("proxy(r3)", query, true, "/")).setCode(code).setNoCaching());
				return true;
			}
			final InputStream input = method.getResponseBodyAsStream();
			if (input == null) {
				query.getResponseTarget().apply(ServerProxy.applyHeaders(method, Reply.empty("proxy(e)", query)).setCode(code));
				return true;
			}
			final TransferBuffer buffer = Transfer.createBuffer(input);
			query.getResponseTarget().apply(ServerProxy.applyHeaders(method, Reply.binary("eAtlas-proxy(s)", query, buffer)).setCode(code));
			return true;
		} catch (final Exception e) {
			throw new RuntimeException(this.getClass().getSimpleName(), e);
		} finally {
			method.releaseConnection();
		}
	}
}
