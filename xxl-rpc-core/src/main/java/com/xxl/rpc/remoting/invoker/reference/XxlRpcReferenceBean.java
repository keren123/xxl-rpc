package com.xxl.rpc.remoting.invoker.reference;

import com.xxl.rpc.registry.ServiceRegistry;
import com.xxl.rpc.remoting.net.Client;
import com.xxl.rpc.remoting.net.NetEnum;
import com.xxl.rpc.remoting.net.params.CallType;
import com.xxl.rpc.remoting.net.params.XxlRpcRequest;
import com.xxl.rpc.remoting.net.params.XxlRpcResponse;
import com.xxl.rpc.remoting.provider.XxlRpcProviderFactory;
import com.xxl.rpc.serialize.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Random;
import java.util.TreeSet;
import java.util.UUID;

/**
 * rpc reference bean, proxy
 *
 * @author xuxueli 2015-10-29 20:18:32
 */
public class XxlRpcReferenceBean {
	private static final Logger logger = LoggerFactory.getLogger(XxlRpcReferenceBean.class);
	// [tips01: save 30ms/100invoke. why why why??? with this logger, it can save lots of time.]


	// ---------------------- config ----------------------

	private NetEnum netcom = NetEnum.NETTY;
	private Serializer serializer;
	private String address;
	private String accessToken;

	private Class<?> iface;
	private String version;

	private long timeout = 5000;	// million
	private CallType callType;

	private ServiceRegistry serviceRegistry;

	public XxlRpcReferenceBean(){	}

	public XxlRpcReferenceBean(NetEnum netcom,
							   Serializer serializer,
							   String address,
							   String accessToken,
							   Class<?> iface,
							   String version,
							   long timeout,
							   CallType callType,
							   ServiceRegistry serviceRegistry) throws Exception {

		initConfig(netcom, serializer, address, accessToken, iface, version, timeout, callType, serviceRegistry);
	}

	public void initConfig(NetEnum netcom,
						   Serializer serializer,
						   String address,
						   String accessToken,
						   Class<?> iface,
						   String version,
						   long timeout,
						   CallType callType,
						   ServiceRegistry serviceRegistry) throws Exception {

		this.netcom = netcom;
		this.serializer = serializer;
		this.address = address;
		this.accessToken = accessToken;
		this.iface = iface;
		this.version = version;
		this.timeout = timeout;
		this.callType = callType;

		// init Client
		initClient();
	}

	// get
	public Serializer getSerializer() {
		return serializer;
	}
	public long getTimeout() {
		return timeout;
	}

	// ---------------------- initClient ----------------------

	Client client = null;

	private void initClient() throws Exception {
		client = netcom.clientClass.newInstance();
		client.init(this);
	}


	// ---------------------- util ----------------------

	public String routeAddress(){
		String addressItem = address;
		if (addressItem!=null && addressItem.trim().length()>0) {
			return addressItem;
		}

		if (serviceRegistry != null) {
			String serviceKey = XxlRpcProviderFactory.makeServiceKey(iface.getName(), version);
			TreeSet<String> addressSet = serviceRegistry.discovery(serviceKey);
			if (addressSet.size() > 0) {
				addressItem = new ArrayList<String>(addressSet).get(new Random().nextInt(addressSet.size()));
			}
		}
		return addressItem;
	}

	public Object getObject() throws Exception {
		return Proxy.newProxyInstance(Thread.currentThread()
				.getContextClassLoader(), new Class[] { iface },
				new InvocationHandler() {
					@Override
					public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
						String className = method.getDeclaringClass().getName();

						// filter method like "Object.toString()"
						if (Object.class.getName().equals(className)) {
							logger.error(">>>>>>>>>>> xxl-rpc proxy class-method not support [{}.{}]", method.getDeclaringClass().getName(), method.getName());
							throw new RuntimeException("xxl-rpc proxy class-method not support");
						}

						// address
						String address = routeAddress();
						if (address==null || address.trim().length()==0) {
							throw new RuntimeException("xxl-rpc reference bean["+ className +"] address empty");
						}

						// request
						XxlRpcRequest xxlRpcRequest = new XxlRpcRequest();
	                    xxlRpcRequest.setRequestId(UUID.randomUUID().toString());
	                    xxlRpcRequest.setCreateMillisTime(System.currentTimeMillis());
	                    xxlRpcRequest.setAccessToken(accessToken);
	                    xxlRpcRequest.setClassName(className);
	                    xxlRpcRequest.setMethodName(method.getName());
	                    xxlRpcRequest.setParameterTypes(method.getParameterTypes());
	                    xxlRpcRequest.setParameters(args);
	                    
	                    // send
	                    XxlRpcResponse xxlRpcResponse = null;
	                    try {
							xxlRpcResponse = client.send(address, xxlRpcRequest);
						} catch (Throwable throwable) {
							xxlRpcResponse = new XxlRpcResponse();
							xxlRpcResponse.setError(throwable);
						}
	                    
	                    // valid xxlRpcResponse
						if (xxlRpcResponse == null) {
							logger.error(">>>>>>>>>>> xxl-rpc netty xxlRpcResponse not found.");
							throw new Exception(">>>>>>>>>>> xxl-rpc netty xxlRpcResponse not found.");
						}
	                    if (xxlRpcResponse.isError()) {
	                        throw xxlRpcResponse.getError();
	                    } else {
	                        return xxlRpcResponse.getResult();
	                    }
	                   
					}
				});
	}


	public Class<?> getObjectType() {
		return iface;
	}

}