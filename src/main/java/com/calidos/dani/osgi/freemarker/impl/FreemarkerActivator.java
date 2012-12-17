/*
*  Copyright 2010 Daniel Giribet <dani - calidos.com>
*
*  Licensed under the Apache License, Version 2.0 (the "License");
*  you may not use this file except in compliance with the License.
*  You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*      
*  Unless required by applicable law or agreed to in writing, software
*  distributed under the License is distributed on an "AS IS" BASIS,
*  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*  See the License for the specific language governing permissions and
*  limitations under the License.
*//////////////////////////////////////////////////////////////////////////////

package com.calidos.dani.osgi.freemarker.impl;

import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.BundleTracker;


import freemarker.template.Configuration;

/**	Activator that does the following:
*	<ol>
*	<li>Looks for a 'freemarker.template.Configuration' service and if present gets an instance</li>
*	<li>If that is not present creates a default freemarker configuration object with the default
*		object wrapper and no cache refresh</li>
*	<li>With that proceeds to track bundles having templates on them</li>
*	</ol>
*	Please @see {@link TemplateTracker} for details of template tracking and URLs to be used.
*	@author daniel giribet
*//////////////////////////////////////////////////////////////////////////////
public class FreemarkerActivator implements BundleActivator {
	
	protected static Logger log = Logger.getLogger(FreemarkerActivator.class);

	private TemplateTracker						templateTracker;
	private BundleTracker<Object>				tracker;
	private ServiceRegistration<Configuration> 	registration;
	

	/* (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 *//////////////////////////////////////////////////////////////////////////////
	@Override
	public void start(BundleContext context) throws Exception {
		
		
		Collection<ServiceReference<Configuration>> serviceReferences = context.getServiceReferences(Configuration.class, "(preparedConfiguration=true)");

		if (serviceReferences!=null && !serviceReferences.isEmpty()) {
		
			Configuration freemarkerConfig = getFreemarkerConfiguration(context, serviceReferences);
			templateTracker = new TemplateTracker(freemarkerConfig);
		
		} else {
			
			log.trace("Creating our own freemarker default configuration");
			
			templateTracker = new TemplateTracker();
		}
				
		tracker = new BundleTracker<Object>(context, Bundle.RESOLVED, templateTracker);
	    tracker.open();
	    
	    // if there is an original service reference object we shouldn't have to publish it again as we're reusing it
	    if (serviceReferences==null || serviceReferences.isEmpty()) {
	    	
	    	log.trace("Registering Freemarker dynamic configuration service");
	    	
	    	Hashtable<String, String> props = new Hashtable<String, String>(1);
	    	props.put("dynamicConfiguration", "true");
	    	registration = context.registerService(Configuration.class, templateTracker.getFreemarkerConfiguration(), props);
	    	
	    }
	    
	} // start


	/* (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 *//////////////////////////////////////////////////////////////////////////////
	@Override
	public void stop(BundleContext context) throws Exception {
	
		if (tracker!=null) {
			tracker.close();
		}
		
		if (registration!=null) {
			registration.unregister();
		}
				
	} // stop


	private Configuration getFreemarkerConfiguration(BundleContext context,
			Collection<ServiceReference<Configuration>> serviceReferences) {

		Iterator<ServiceReference<Configuration>> iterator = serviceReferences.iterator();
		ServiceReference<Configuration> configurationServiceReference = iterator.next();
		Configuration freemarkerConfig = (Configuration) context.getService(configurationServiceReference);

		return freemarkerConfig;

	}

}
