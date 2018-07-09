package ru.myx.srv.proxy;

import ru.myx.ae3.produce.Produce;

/*
 * Created on 27.09.2003
 * 
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
/**
 * @author barachta
 * 
 *         To change the template for this generated type comment go to
 *         Window>Preferences>Java>Code Generation>Code and Comments
 */
public final class Main {
	/**
	 * @param args
	 */
	public static final void main(final String[] args) {
		System.out.println( "RU.MYX.AE1SRV.PROXY: server type factory is initializing..." );
		Produce.registerFactory( new FactoryProxy() );
		System.out.println( "RU.MYX.AE1SRV.PROXY: done." );
	}
}
