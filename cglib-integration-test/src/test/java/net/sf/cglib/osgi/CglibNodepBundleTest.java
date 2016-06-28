package net.sf.cglib.osgi;

import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.wrappedBundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;

import net.sf.cglib.beans.BeanCopier;
import net.sf.cglib.core.ClassGenerator;
import net.sf.cglib.core.internal.Function;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.transform.ClassFilter;
import net.sf.cglib.transform.impl.FieldProvider;
import net.sf.cglib.util.ParallelSorter;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class CglibNodepBundleTest {
	
	@Configuration
	public Option[] options() {
		return new Option[] { 
				mavenBundle("cglib", "cglib-nodep").versionAsInProject()
		};
	}

	/**
	 * Loads a class from any exported package. No ClassNotFoundException should
	 * be caused to be thrown.
	 */
	@Test
	public void verifyExports() throws Exception {		
		BeanCopier.class.getName();
		ClassGenerator.class.getName();
		Function.class.getName();
		Callback.class.getName();
		FastClass.class.getName();
		ClassFilter.class.getName();
		FieldProvider.class.getName();
		ParallelSorter.class.getName();
		getClass().getClassLoader().loadClass("net.sf.cglib.asm.$AnnotationVisitor");
	}
}
