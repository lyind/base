/*
 * Copyright (C) 2017  Jonas Zeiger <jonas.zeiger@talpidae.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.talpidae.base.resource;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.OptionalBinder;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerFactory;

import lombok.val;


/**
 * For some reason a few required classes do not get bound by JerseyGuiceModule, so we do it manually.
 */
public class JerseySupportModule extends AbstractModule
{
    private final static DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY;

    static
    {
        val documentBuilderFactory = DocumentBuilderFactory.newInstance();

        documentBuilderFactory.setExpandEntityReferences(false);
        documentBuilderFactory.setNamespaceAware(true);
        try
        {
            documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        }
        catch (ParserConfigurationException | Error e)
        {
            // ignore
        }

        DOCUMENT_BUILDER_FACTORY = documentBuilderFactory;
    }

    @Override
    protected void configure()
    {
        // allow jersey-guice2 to work without error messages
        bind(SAXParserFactory.class).toInstance(SAXParserFactory.newInstance());
        bind(TransformerFactory.class).toInstance(TransformerFactory.newInstance());
        bind(DocumentBuilderFactory.class).toInstance(DOCUMENT_BUILDER_FACTORY);

        OptionalBinder.newOptionalBinder(binder(), CredentialValidator.class).setDefault().to(DenyAllCredentialValidator.class);
    }
}
