package thewebsemantic;

import java.beans.PropertyDescriptor;
import static thewebsemantic.TypeWrapper.*;
import java.util.ArrayList;
import java.util.Collection;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.shared.Lock;

/**
 * Converts a simple java bean to RDF, provided it's annotated with
 * <code>Namespace</code>. To make a bean persitable by jenabean, you are
 * merely required to add the Namespace annotation. By default public bean
 * properties are converted to rdf properties by appending "has" and proper
 * casing the property name. For example, a bean with methods getName() and
 * setName() would result in the RDF property "hasName", with the namespace
 * given in the classes Namespace annotation.
 * <br/><br/>
 * The default behavior for rdf property naming is overridden by using the
 * RdfProperty annotation along with the getter method. The value supplied to
 * the RdfProperty annotation is taken as the full RDF property URI.
 * <br/><br/>
 * The bean itself is typed using the Namespace along with the bean name, for
 * example, Book.class with namespace "http://example.org/" becomes rdf type
 * "http://example.org/Book".
 * <br/><br/>
 * Here's a simple example of a bean that's ready to be saved:
 * <pre>
 * <code>
 * &#64;Namespace("http://example.org/")
 * public Book {
 *    private String name;
 *    public void setName(String s) { name=s;}
 *    public String getName() {return name;}
 * }
 * </code>
 * </pre>
 * It uses the Namespace annotation.  Bean2RDF takes care of the rest.
 * 
 * @author Taylor Cowan
 * @see Namespace
 * @see Id
 * @see RdfProperty
 */
public class Bean2RDF extends Base {

	private ArrayList<Object> cycle;

	public Bean2RDF(OntModel m) {
		super(m);
	}

	public synchronized Resource write(Object bean) {
		if (!isAnnotated(bean))
			return null;
		m.enterCriticalSection(Lock.WRITE);
		cycle = new ArrayList<Object>();
		Resource r = _write(bean);
		m.leaveCriticalSection();
		return r;
	}

	private Resource _write(Object bean) {
		Resource rdfType = m.createClass(type(bean).rdfTypeName());
		Resource rdfResource = findOrNew(rdfType, type(bean).uri(bean));
		if (cycle.contains(bean))
			return rdfResource;
		cycle.add(bean);
		return write(bean, rdfResource);
	}

	private Resource findOrNew(Resource rdfType, String uri) {
		return m.createResource(uri, rdfType);
	}

	private Resource write(Object bean, RDFNode node) {
		return write(bean, (Resource) node.as(Resource.class));
	}

	protected Resource write(Object bean, Resource subject) {
		try {
			for (PropertyDescriptor p : type(bean).descriptors()) {
				if (p.getWriteMethod() == null )
					continue;
				Object o = p.getReadMethod().invoke(bean);
				if (o == null)
					continue;
				Property property = toRdfProperty(ns(bean), p);
				if ( o instanceof Collection)
					updateCollection(subject, property, (Collection<?>) o);
				else if (isAnnotated(o))
					updateOrAddBindable(subject, property, o);
				else
					getSaver(subject, property).write(o);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return subject;
	}

	/**
	 * update a one to many property.
	 * 
	 * @param subject
	 * @param property
	 * @param c
	 */
	private void updateCollection(Resource subject, Property property,
			Collection<?> c) {
		subject.removeAll(property);
		AddSaver saver = new AddSaver(subject, property);
		for (Object o : c)
			if (isAnnotated(o))
				subject.addProperty(property, _write(o)); //recursive
			else
				saver.write(o); //leaf
	}

	/**
	 * To simplify the type switch above, return a specialized helper either for
	 * updating an existing relation, or adding a new relation, depending on 
	 * the existence of property on resource s.
	 * 
	 * @param s
	 * @param property
	 * @return appropriate saver implementation
	 */
	private Saver getSaver(Resource s, Property property) {
		return (s.getProperty(property) == null) ? new CreateSaver(s, property)
				: new UpdateSaver(s, property);
	}

	/**
	 * Update or persist non-JDK Object to RDF. This would be any domain object
	 * outside String, Date, and the usual primitive types.
	 * 
	 * @param subject
	 * @param property
	 * @param o
	 */
	protected void updateOrAddBindable(Resource subject, Property property,
			Object o) {
		Statement existingRelation = subject.getProperty(property);
		if (existingRelation != null)
			write(o, existingRelation.getObject());
		else
			subject.addProperty(property, _write(o));
	}
}

/*
 * Copyright (c) 2007 Taylor Cowan
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * 
 */