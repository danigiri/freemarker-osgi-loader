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

import org.osgi.framework.Bundle;

/**
*	@author daniel giribet
*//////////////////////////////////////////////////////////////////////////////
public class TemplateEntry {

	
	private URL templateURL;
	private Bundle bundle;

	public TemplateEntry(Bundle bundle, URL templateURL) {
		
		this.bundle = bundle;
		this.templateURL = templateURL;
		
	}	// TemplateEntry
	

	/* (non-Javadoc)
	* @see java.lang.Object#equals(java.lang.Object)
	*//////////////////////////////////////////////////////////////////////////////
	@Override
	public boolean equals(Object obj) {
		
		if (!(obj instanceof TemplateEntry)) {
			return false;
		}
		TemplateEntry te = (TemplateEntry)obj;
		return bundle.getBundleId() == te.bundle.getBundleId() && templateURL.equals(te.templateURL);
		
	}	// equals
	
	
	/** @return Returns the templateURL.
	*//////////////////////////////////////////////////////////////////////////////
	public URL getTemplateURL() {
		
		return templateURL;
		
	}	// getTemplateURL

	
}
