/*
 * Created on 20.10.2003
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package ru.myx.srv.proxy;

import ru.myx.ae1.know.Server;
import ru.myx.ae3.Engine;
import ru.myx.ae3.base.Base;
import ru.myx.ae3.base.BaseObject;
import ru.myx.ae3.produce.ObjectFactory;

/**
 *
 * @author myx
 *
 */
public final class FactoryProxy implements ObjectFactory<Object, Server> {
	
	private static final Class<?>[] TARGETS = {
			Server.class
	};

	private static final Class<?>[] SOURCES = null;

	private static final String[] VARIETY = {
			"ae1:PROXY"
	};

	@Override
	public boolean accepts(final String variant, final BaseObject attributes, final Class<?> source) {
		
		return true;
	}

	@Override
	public Server produce(final String variant, final BaseObject attributes, final Object source) {
		
		final String id = Base.getString(attributes, "id", Engine.createGuid());
		final String address = Base.getString(attributes, "address", "*");
		final String port = Base.getString(attributes, "port", "*");
		return new ServerProxy(id, address, port);
	}

	@Override
	public Class<?>[] sources() {
		
		return FactoryProxy.SOURCES;
	}

	@Override
	public Class<?>[] targets() {
		
		return FactoryProxy.TARGETS;
	}

	@Override
	public String[] variety() {
		
		return FactoryProxy.VARIETY;
	}
}
