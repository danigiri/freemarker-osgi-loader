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


import java.net.URL;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Stack;

import org.apache.log4j.Logger;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.util.tracker.BundleTrackerCustomizer;

import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.cache.URLTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;

/** Template tracker that tracks bundles added to the OSGi environment.
*	Whenever there is a bundle that includes the manifest header 'Freemarker-Templates'
*	it reads its value and uses that as a local path within the bundle where to read templates from.
*	Templates are added with the following id's: <i>
*		<ul>
*			<li>'bundle://BUNDLENAME:BUNDLEVERSION/RELATIVETEMPLATEPATH</li>
*			<li>'bundle://BUNDLENAME/RELATIVETEMPLATEPATH</li>
*			<li>'RELATIVETEMPLATEPATH</li>
*		</ul></i><p>
*	
*	<b>PLEASE NOTE</b>: the value of the 'Freemarker-Templates' header is removed from the path of the template URLs!!!<p/>
*
*	Template users can use any of the forms to locate templates but should note that the different location
*	URLs can be used in the case of name clashes or multiple versions of the same bundle being present.<p/>
*	It also maintains a stack of templates for each template ID using a LIFO policy. This can be used
*	in clever ways: for instance, using a given template in normal circumstances but whenever a slight modification
*	to the template is needed temporally (for instance, to display debug information) it can be dinamically added
*	to the environment for a while and then removed safely.
* 	@author daniel giribet
*//////////////////////////////////////////////////////////////////////////////
public class TemplateTracker implements BundleTrackerCustomizer<Object> {

	private static final String TEMPLATE_HEADER = "Freemarker-Templates";
	
	private Configuration 		 		  		freemarkerConfig;
	private Map<String, Stack<TemplateEntry>>	templates;
	private Map<Long, Set<URL>>					templatesOfEachBundle;
	
	protected static Logger log = Logger.getLogger(TemplateTracker.class);
	
	
	/** Create a tracker with the default freemarker configuration and our dynamic template loader
	*//////////////////////////////////////////////////////////////////////////////
	public TemplateTracker() {
		
		log.debug("Creating template tracker with default configuration...");

		freemarkerConfig = new Configuration();
		freemarkerConfig.setObjectWrapper(new DefaultObjectWrapper());
		freemarkerConfig.setTemplateUpdateDelay(Integer.MAX_VALUE);
		freemarkerConfig.setLocalizedLookup(false);
		
		setupTemplateHolderStructures();
		setupDynamicTemplateLoader();
		
	}	// TemplateTracker
	
	
	/** Constructor, allocates memory
	* @param configuration to be used by the tracker
	*//////////////////////////////////////////////////////////////////////////////
	public TemplateTracker(Configuration configuration) {
		
		log.debug("Creating template tracker with existing configuration...");
		
		freemarkerConfig = configuration;
		setupTemplateHolderStructures();
		setupDynamicWithExistingTemplateLoder(configuration.getTemplateLoader());		
		
	}	// TemplateTracker


	/* (non-Javadoc)
	 * @see org.osgi.util.tracker.BundleTrackerCustomizer#addingBundle(org.osgi.framework.Bundle, org.osgi.framework.BundleEvent)
	 *//////////////////////////////////////////////////////////////////////////////
	@Override
	public Object addingBundle(Bundle bundle, BundleEvent event) {
		
		// we look for the custom header and act accordingly
	
		long bundleId = bundle.getBundleId();
	
		if (log.isTraceEnabled()) {
			log.trace("addingBundle callback for bundle " + bundleId + ", checking if it contains custom freemarker header");
		}
	
		Dictionary<String, String> headers = bundle.getHeaders();
		if (headers == null) {
			log.warn("Null headers from bundle: " + bundleId);
			return null;
		}
	
		String templatesLocation = (String) headers.get(TEMPLATE_HEADER);
		if (templatesLocation == null) {
			log.warn("Null templates location value from bundle: " + bundleId);
			return null;
		}
	
		log.debug("Adding templates from bundle :" + bundleId);
		if (log.isTraceEnabled()) {
			log.trace("Using template location: " + templatesLocation);
		}
	
		Enumeration<URL> bundleTemplates = bundle.findEntries(templatesLocation, "*.ftl", true);
	
		if (bundleTemplates == null) {
			log.warn("Null template entries object for bundle: " + bundleId);
			return null;
		}
	
		HashSet<URL> templatesFromAddedBundle = new HashSet<URL>();
	
		while (bundleTemplates.hasMoreElements()) {
	
			// we get the template URL and add it to the general template list as well as the template
			// list of that bundle
			URL templateURL = bundleTemplates.nextElement();
			addTemplate(bundle, templateURL, templatesLocation);
			templatesFromAddedBundle.add(templateURL);
	
			log.info("Added template: '" + templateURL + "'");
		}	
		templatesOfEachBundle.put(bundleId, templatesFromAddedBundle);
	
		return null;
		
	}	// addingBundle


	/* (non-Javadoc)
	 * @see org.osgi.util.tracker.BundleTrackerCustomizer#modifiedBundle(org.osgi.framework.Bundle, org.osgi.framework.BundleEvent, java.lang.Object)
	 *//////////////////////////////////////////////////////////////////////////////
	@Override
	public void modifiedBundle(Bundle bundle, BundleEvent event, Object object) {
	
		// the bundle has been updated, the easiest way to update the templates if to act as if the
		// bundle had been removed and then added anew
		
		log.debug("Modified template-holding bundle id:"+bundle.getBundleId());
		
		removedBundle(bundle,event,object);
		addingBundle(bundle, event);		
		
	}	// modifiedBundle


	/* (non-Javadoc)
	 * @see org.osgi.util.tracker.BundleTrackerCustomizer#removedBundle(org.osgi.framework.Bundle, org.osgi.framework.BundleEvent, java.lang.Object)
	 *//////////////////////////////////////////////////////////////////////////////
	@Override
	public void removedBundle(Bundle bundle, BundleEvent event, Object object) {
		
		// we check for the header and if present proceed to remove the templates from our configuration
		
		freemarkerConfig.clearTemplateCache();
		
		String templatesLocation = (String) bundle.getHeaders().get(TEMPLATE_HEADER);
		if (templatesLocation!=null) {
	
			log.debug("Removing templates from bundle:"+bundle.getBundleId());
		
			Set<URL> oldTemplatesFromModifiedBundle = templatesOfEachBundle.get(bundle.getBundleId());
			
			if (oldTemplatesFromModifiedBundle==null) {
				throw new NullPointerException("Trying to remove or alter templates of a bundle that didn't have any");
			}
			
			Iterator<URL> templateList = oldTemplatesFromModifiedBundle.iterator();
			while (templateList.hasNext()) {
				URL templateURL = templateList.next();
				
				String templateID = getTemplateID(templateURL, templatesLocation);
				removeTemplateFromStack(bundle,templateURL,templateID);
				
				String bundleTemplateID = getBundleTemplateID(bundle, templateID);
				removeTemplateFromStack(bundle, templateURL, bundleTemplateID);
				
				String bundleVersionTemplateID = getBundleVersionTemplateID(bundle, templateID);
				removeTemplateFromStack(bundle, templateURL, bundleVersionTemplateID);
	
			}
			
			templatesOfEachBundle.remove(oldTemplatesFromModifiedBundle);
			
		} // if
		
	}	// removedBundle


	/** We configure the freemarker configuration to use the loader that gets templates
	*	from bundles.
	*//////////////////////////////////////////////////////////////////////////////
	protected void setupDynamicTemplateLoader() {
		
		freemarkerConfig.setTemplateLoader( new URLTemplateLoader() {
			
			@Override
			protected URL getURL(String url) {
		
				if (log.isTraceEnabled()) {
					log.trace("Getting template url="+url);
				}
				
				Stack<TemplateEntry> templateStack = templates.get(url);
				if (templateStack!=null) {
					TemplateEntry templateStackTop = templateStack.peek();
					if (templateStackTop!=null) {
						return templateStackTop.getTemplateURL();
					}
					return null;
				}
				return null;

			}

		});
		
	}	// setupDynamicTemplateLoader
	
	

	/** We are supplied a template loader that will be used in conjunction with the bundle
	*	template loader, having the supplied one with more priority.
	* 	@param existingTemplateLoader supplied loader, if NULL then bundle loader is used
	*//////////////////////////////////////////////////////////////////////////////
	protected void setupDynamicWithExistingTemplateLoder(TemplateLoader existingTemplateLoader) {

		if (existingTemplateLoader!=null) {
			
			setupDynamicTemplateLoader();
			TemplateLoader dynamicTemplateLoader = freemarkerConfig.getTemplateLoader();			
			freemarkerConfig.setTemplateLoader(new MultiTemplateLoader(new TemplateLoader[] {existingTemplateLoader, dynamicTemplateLoader}));
			
		} else {
			log.warn("Service freemarker Configuration had no template loader, using default bundle loader");
			setupDynamicTemplateLoader();
	 	}
		
	} 	// addBundleTemplateLoaderTo


	/**
	* 
	*//////////////////////////////////////////////////////////////////////////////
	private void setupTemplateHolderStructures() {
		templates = new HashMap<String, Stack<TemplateEntry>>();
		templatesOfEachBundle = new HashMap<Long, Set<URL>>();
	}


	/** We add a template by its simplest name 'path/file.ftl', 'bundle://bundlename/path/file.ftl' and
	* 	'bundle://bundlename:version/path/file.ftl'. We also keep track of the bundle that added the template
	*	@param bundle that contains the template
	*	@param templateURL the URL pointing to the template file itself
	*//////////////////////////////////////////////////////////////////////////////
	private void addTemplate(Bundle bundle, URL templateURL, String templatePathPrefix) {
		
		if (log.isTraceEnabled()) {
			log.trace("Adding template on url="+templateURL);
		}
		
		String templateID = getTemplateID(templateURL, templatePathPrefix);
		addTemplateToStack(bundle, templateURL, templateID);
		
		String bundleTemplateID = getBundleTemplateID(bundle, templateID);
		addTemplateToStack(bundle, templateURL, bundleTemplateID);
		
		String bundleVersionTemplateID = getBundleVersionTemplateID(bundle, templateID);
		addTemplateToStack(bundle, templateURL, bundleVersionTemplateID);
		
	}	// addTemplate



	/** Helper function to add a template to a stack 
	* @param bundle			containing the new template
	* @param templateURL	template file URL
	* @param templateID		identifier in the templates list
	*//////////////////////////////////////////////////////////////////////////////
	private void addTemplateToStack(Bundle bundle, URL templateURL, String templateID) {
	
		TemplateEntry newTemplate = new TemplateEntry(bundle,templateURL);
	
		Stack<TemplateEntry> templateStack = templates.get(templateID);		
		if (templateStack==null) {
			templateStack = new Stack<TemplateEntry>();
		}
		templates.put(templateID, templateStack);
		
		templateStack.push(newTemplate);
		log.debug("Added template to stack: '"+templateID+"'");
	
	}	// addTemplateToStack



	/** Helper function to look for a template in the stack and then remove it
	* @param bundle			which one originally contained it
	* @param templateURL	url
	* @param templateID		template identifier to be removed
	*//////////////////////////////////////////////////////////////////////////////
	private void removeTemplateFromStack(Bundle bundle, URL templateURL,
			String templateID) {
		TemplateEntry templateToRemove = new TemplateEntry(bundle,templateURL);
		
		Stack<TemplateEntry> templateStack = templates.get(templateID);		
		if (templateStack==null) {
			throw new NoSuchElementException("Trying to remove template not present in template stack");
		}
		templateStack.remove(templateToRemove);
		if (templateStack.isEmpty()) {
			templates.remove(templateID);
		}

	}	// removeTemplateFromStack



	/**	Generate the simplest template id (the template path), taking care to remove the leading template location and '/'
	* @param templateURL		containing TEMPLATELOCATION/RELATIVEPATH
	* @param templatePathPrefix TEMPLATELOCATION to be remove d
	* @return cleaned path
	*//////////////////////////////////////////////////////////////////////////////
	private String getTemplateID(URL templateURL, String templatePathPrefix) {
		
		String templatePath = templateURL.getPath();
		
		// first of all we add the simplest entry 'path/file'
		String templateID = templatePath.substring(templatePathPrefix.length()+1);
		if (templateID.startsWith("/")) {
			templateID = templateID.substring(1);
		}
		return templateID;
		
	}	// getTemplateID



	/** Create template URL id of the form 'bundle://BUNDLENAME/RELATIVEPATH'
	* @param bundle		bundle containing the template
	* @param templateID	RELATIVEPATH
	* @return built ID
	*//////////////////////////////////////////////////////////////////////////////
	private String getBundleTemplateID(Bundle bundle, String templateID) {

		return "bundle://"+bundle.getSymbolicName()+"/"+templateID;

	}	// getBundleTemplateID



	/** Create template URL id of the form 'bundle://BUNDLENAME:BUNDLEVERSION/RELATIVEPATH'
	* @param bundle containing the template, to extract the name
	* @param templateID relative path of the template
	* @return created URL
	*//////////////////////////////////////////////////////////////////////////////
	private String getBundleVersionTemplateID(Bundle bundle, String templateID) {
		
		return "bundle://"+bundle.getSymbolicName()+":"+bundle.getVersion()+"/"+templateID;

	}	// getBundleVersionTemplateID


	/** @return freemarker configuration being used by the tracker
	*//////////////////////////////////////////////////////////////////////////////
	public Configuration getFreemarkerConfiguration() {
		return freemarkerConfig;
	}
	

}	// TemplateTracker



