/*******************************************************************************
 * Copyright (c) 2004, 2010 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *     Ken Sueda - improvements
 *     Jevgeni Holodkov - improvements
 *******************************************************************************/

package io.opensemantics.prototype.contrib.org.eclipse.mylyn.internal.tasks.core.externalization;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import io.opensemantics.prototype.contrib.org.eclipse.mylyn.internal.tasks.core.ITasksCoreConstants;
import io.opensemantics.signifier.api.Callsite;
import io.opensemantics.signifier.api.Method;

import static io.opensemantics.prototype.contrib.org.eclipse.mylyn.internal.commons.core.XmlStringConverter.cleanXmlString;

public class TaskListExternalizer {

  private int offset;
  
  public TaskListExternalizer() {
    this.offset = 1000;
  }
  public TaskListExternalizer(int offset) {
    this.offset = offset;
  }

  private static final String TRANSFORM_PROPERTY_VERSION = "version"; //$NON-NLS-1$

  // May 2007: There was a bug when reading in 1.1
  // Result was an infinite loop within the parser
  private static final String XML_VERSION = "1.0"; //$NON-NLS-1$

  public void writeTaskList(List<Callsite> taskList, File outFile) throws CoreException {
    try {
      FileOutputStream outStream = new FileOutputStream(outFile);
      try {
        Document doc = createTaskListDocument(taskList);

        ZipOutputStream zipOutStream = new ZipOutputStream(outStream);

        ZipEntry zipEntry = new ZipEntry(ITasksCoreConstants.OLD_TASK_LIST_FILE);
        zipOutStream.putNextEntry(zipEntry);
        zipOutStream.setMethod(ZipOutputStream.DEFLATED);

        writeDocument(doc, zipOutStream);

        zipOutStream.flush();
        zipOutStream.closeEntry();
        zipOutStream.finish();
      } finally {
        outStream.close();
      }
    } catch (IOException e) {
      throw new CoreException(new Status(IStatus.ERROR, ITasksCoreConstants.ID_PLUGIN, "Saving Task List failed", //$NON-NLS-1$
          e));
    }
  }

  private Document createTaskListDocument(List<Callsite> callsites) {

    Document doc;
    Element root;

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder;
    try {
      builder = factory.newDocumentBuilder();
      doc = builder.newDocument();
      root = doc.createElement("TaskList");
      root.setAttribute("Version", "2.0");
      doc.appendChild(root);
      
      for (Callsite callsite: callsites) {
        addCallsite(callsite, doc, root);
      }
    } catch (ParserConfigurationException e) {
      doc = null;
    }
    
    return doc;
  }

  private void addCallsite(Callsite callsite, Document doc, Element root) {
    if (doc == null) return;

    for (Method callee: callsite.getCallers()) {
      final Element node = doc.createElement("Task");
      node.setAttribute("Active", "false");
      node.setAttribute("ConnectorKind", "local");
      SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S Z", Locale.ENGLISH);
      node.setAttribute("CreationDate", format.format(new Date()));
      node.setAttribute("DueDate", "");
      node.setAttribute("EndDate", "");
      node.setAttribute("Estimated", "0");
      node.setAttribute("Handle", "local-" + offset);
      node.setAttribute("Kind", "task");
      node.setAttribute("Label", cleanXmlString(
          String.format("%s in %s", callsite.getCallee().toString(), callee.toString())));
      node.setAttribute("LastModified", "now");
      node.setAttribute("MarkReadPending", "false");
      node.setAttribute("ModificationDate", "");
      node.setAttribute("Notes", cleanXmlString(callee.getBody()));
      node.setAttribute("NotifiedIncoming", "true");
      node.setAttribute("Owner", "local");
      node.setAttribute("Priority", "P3");
      node.setAttribute("Reminded", "false");
      node.setAttribute("RepositoryUrl", "local");
      node.setAttribute("Stale", "false");
      node.setAttribute("TaskId", String.valueOf(offset));
      offset++; // now increment
      node.setAttribute("offlineSyncState", "SYNCHRONIZED");
      root.appendChild(node);
    }
  }
  
  
  private void writeDocument(Document doc, OutputStream outputStream) throws CoreException {
    Source source = new DOMSource(doc);
    Result result = new StreamResult(outputStream);
    try {
      Transformer xformer = TransformerFactory.newInstance().newTransformer();
      xformer.setOutputProperty(TRANSFORM_PROPERTY_VERSION, XML_VERSION);
      xformer.transform(source, result);
    } catch (TransformerException e) {
      throw new CoreException(new Status(IStatus.ERROR, ITasksCoreConstants.ID_PLUGIN, "Failed write task list", //$NON-NLS-1$
          e));
    }
  }
}
