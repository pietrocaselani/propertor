package propertor;

import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCPrimitiveTypeTree;
import com.sun.tools.javac.tree.JCTree.JCReturn;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Name.Table;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.Set;

import static com.sun.tools.javac.code.TypeTags.VOID;
import static com.sun.tools.javac.util.Name.fromString;

/**
 * Created by Pietro Caselani
 * On 9/30/14
 * Propertor
 */
@SupportedAnnotationTypes("propertor.Property")
public class PropertyProcessor extends AbstractProcessor {
	//region Fields
	private TreeMaker mTreeMaker;
	//endregion

	//region Processor
	@Override public synchronized void init(ProcessingEnvironment processingEnvironment) {
		super.init(processingEnvironment);

		mTreeMaker = TreeMaker.instance(((JavacProcessingEnvironment) processingEnvironment).getContext());
	}

	@Override public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
		final ArrayList<PropertyModel> propertyModels = parsePropertyAnnotations(roundEnvironment);

		for (final PropertyModel propertyModel : propertyModels) {
			writeGetter(propertyModel);
			writeSetter(propertyModel);
		}

		return false;
	}

	@Override public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}
	//endregion

	//region Setter Getter writer
	private void writeSetter(PropertyModel propertyModel) {
		final Trees trees = Trees.instance(processingEnv);

		final JCClassDecl classTree = (JCClassDecl) trees.getTree(propertyModel.getClassElement());

		final Name methodName = getName(propertyModel.getSetterName());

		for (final JCTree def : classTree.defs) {
			if (def instanceof JCMethodDecl) {
				JCMethodDecl methodDecl = (JCMethodDecl) def;
				if (methodDecl.name.equals(methodName)) {
					return;
				}
			}
		}

		long setterFlags;

		switch (propertyModel.getPropertyAnnotation().setter()) {
			case PUBLIC:
				setterFlags = Flags.PUBLIC;
				break;
			case PROTECTED:
				setterFlags = Flags.PROTECTED;
				break;
			case PRIVATE:
				setterFlags = Flags.PRIVATE;
				break;
			default:
				setterFlags = 0;
		}

		setterFlags = setterFlags | Flags.FINAL;

		final JCModifiers modifiers = mTreeMaker.Modifiers(setterFlags);

		final JCTree fieldType = propertyModel.getFieldDecl().getType();

		final JCTree parameterType = fieldType instanceof JCPrimitiveTypeTree ?
				wrapPrimitive((JCPrimitiveTypeTree) fieldType) : fieldType;

		final JCExpression returnExpression = mTreeMaker.TypeIdent(VOID);

		final JCVariableDecl paramDecl = mTreeMaker.VarDef(
				mTreeMaker.Modifiers(Flags.PARAMETER),
				getName(propertyModel.getPropertyName().substring(0, 1).toLowerCase() + propertyModel.getPropertyName().substring(1)),
				mTreeMaker.Ident(getName(parameterType.toString())),
				null);

		final JCIdent paramExpression = mTreeMaker.Ident(paramDecl.name);

		final JCExpression fieldIdent = mTreeMaker.Ident(propertyModel.getFieldDecl());

		final JCAssign assign = mTreeMaker.Assign(fieldIdent, paramExpression);

		final JCExpressionStatement exec = mTreeMaker.Exec(assign);

		final JCBlock setterBody = mTreeMaker.Block(0, List.<JCStatement>nil());

		setterBody.stats = setterBody.stats.append(exec);

		final JCMethodDecl setter = mTreeMaker.MethodDef(modifiers, methodName, returnExpression, List.<JCTypeParameter>nil(),
				List.of(paramDecl), List.<JCExpression>nil(), setterBody, null);

		classTree.defs = classTree.defs.append(setter);
	}

	private void writeGetter(PropertyModel propertyModel) {
		final Trees trees = Trees.instance(processingEnv);

		final JCClassDecl classTree = (JCClassDecl) trees.getTree(propertyModel.getClassElement());

		final Name methodName = getName(propertyModel.getGetterName());

		for (final JCTree def : classTree.defs) {
			if (def instanceof JCMethodDecl) {
				JCMethodDecl methodDecl = (JCMethodDecl) def;
				if (methodDecl.name.equals(methodName)) {
					return;
				}
			}
		}

		long getterFlags;

		switch (propertyModel.getPropertyAnnotation().getter()) {
			case PUBLIC:
				getterFlags = Flags.PUBLIC;
				break;
			case PROTECTED:
				getterFlags = Flags.PROTECTED;
				break;
			case PRIVATE:
				getterFlags = Flags.PRIVATE;
				break;
			default:
				getterFlags = 0;
		}

		getterFlags = getterFlags | Flags.FINAL;

		final JCBlock getterBody = mTreeMaker.Block(0, List.<JCStatement>nil());

		final JCExpression expression = mTreeMaker.Ident(propertyModel.getFieldDecl());

		final JCReturn returnStmt = mTreeMaker.Return(expression);

		getterBody.stats = getterBody.stats.append(returnStmt);

		final JCModifiers modifiers = mTreeMaker.Modifiers(getterFlags);

		final JCTree fieldType = propertyModel.getFieldDecl().getType();
		final JCTree returnType = fieldType instanceof JCPrimitiveTypeTree ?
				wrapPrimitive((JCPrimitiveTypeTree) fieldType) : fieldType;

		final JCExpression returnExpression = mTreeMaker.Ident(getName(returnType.toString()));

		final JCMethodDecl getter = mTreeMaker.MethodDef(modifiers, methodName, returnExpression, List.<JCTypeParameter>nil(),
				List.<JCVariableDecl>nil(), List.<JCExpression>nil(), getterBody, null);

		classTree.defs = classTree.defs.append(getter);
	}
	//endregion

	//region Helpers
	private ArrayList<PropertyModel> parsePropertyAnnotations(RoundEnvironment env) {
		final Set<? extends Element> propertyElements = env.getElementsAnnotatedWith(Property.class);

		ArrayList<PropertyModel> propertyModels = new ArrayList<PropertyModel>(propertyElements.size());

		for (final Element propertyElement : propertyElements) {
			propertyModels.add(new PropertyModel(propertyElement, processingEnv));
		}

		return propertyModels;
	}

	private JCExpression generateDotExpression(String... strings) {
		if (strings == null || strings.length == 0) return null;

		String arg1 = strings[0];

		JCExpression expression = mTreeMaker.Ident(getName(arg1));

		for (int i = 1; i < strings.length; i++) {
			expression = mTreeMaker.Select(expression, getName(strings[i]));
		}

		return expression;
	}

	private Name getName(String s) {
		return fromString(Table.instance(((JavacProcessingEnvironment) processingEnv).getContext()), s);
	}

	private JCExpression wrapPrimitive(JCPrimitiveTypeTree primitiveTypeTree) {
		String primitiveTypeName = primitiveTypeTree.type.tsym.getQualifiedName().toString();
		String wrapperName;

		if (primitiveTypeName.equalsIgnoreCase("boolean")) {
			wrapperName = "Boolean";
		} else if (primitiveTypeName.equalsIgnoreCase("byte")) {
			wrapperName = "Byte";
		} else if (primitiveTypeName.equalsIgnoreCase("char")) {
			wrapperName = "Character";
		} else if (primitiveTypeName.equalsIgnoreCase("double")) {
			wrapperName = "Double";
		} else if (primitiveTypeName.equalsIgnoreCase("float")) {
			wrapperName = "Float";
		} else if (primitiveTypeName.equalsIgnoreCase("int")) {
			wrapperName = "Integer";
		} else if (primitiveTypeName.equalsIgnoreCase("long")) {
			wrapperName = "Long";
		} else if (primitiveTypeName.equalsIgnoreCase("void")) {
			wrapperName = "Void";
		} else {
			throw new IllegalArgumentException("Could not find wrapper for type " + primitiveTypeName);
		}

		return generateDotExpression(wrapperName);
	}
	//endregion
}