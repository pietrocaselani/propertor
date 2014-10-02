package propertor;

import com.sun.source.util.Trees;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Created by Pietro Caselani
 * On 9/30/14
 * Propertor
 */
final class PropertyModel {
	private static TypeMirror sBooleanType;
	private static TypeMirror sBooleanPrimitiveType;
	private ProcessingEnvironment mEnvironment;
	private Element mFieldElement, mClassElement;
	private Property mProperty;
	private String mFieldName, mGetterName, mSetterName;
	private JCVariableDecl mFieldDecl;

	public PropertyModel(Element element, ProcessingEnvironment env) {
		mEnvironment = env;
		setFieldElement(element);
	}

	public Element getFieldElement() {
		return mFieldElement;
	}

	public void setFieldElement(Element fieldElement) {
		mFieldElement = fieldElement;

		mClassElement = fieldElement.getEnclosingElement();

		mProperty = fieldElement.getAnnotation(Property.class);

		final Elements elementUtils = mEnvironment.getElementUtils();
		final Types typeUtils = mEnvironment.getTypeUtils();

		if (sBooleanType == null) {
			sBooleanType = elementUtils.getTypeElement("java.lang.Boolean").asType();
		}

		if (sBooleanPrimitiveType == null) {
			sBooleanPrimitiveType = typeUtils.getPrimitiveType(TypeKind.BOOLEAN);
		}

		final TypeMirror elementType = fieldElement.asType();

		final String getterPrefix = typeUtils.isSameType(elementType, sBooleanPrimitiveType) ||
				typeUtils.isSameType(elementType, sBooleanType) ? "is" : "get";

		mFieldName = fieldElement.getSimpleName().toString();
		String propertyName = mFieldName.substring(1);
		mGetterName = getterPrefix + propertyName;
		mSetterName = "set" + propertyName;

		mFieldDecl = (JCVariableDecl) Trees.instance(mEnvironment).getTree(fieldElement);
	}

	public JCVariableDecl getFieldDecl() {
		return mFieldDecl;
	}

	public Property getPropertyAnnotation() {
		return mProperty;
	}

	public String getPropertyName() {
		return mFieldName.substring(1);
	}

	public String getFieldName() {
		return mFieldName;
	}

	public String getGetterName() {
		return mGetterName;
	}

	public String getSetterName() {
		return mSetterName;
	}

	public Element getClassElement() {
		return mClassElement;
	}
}