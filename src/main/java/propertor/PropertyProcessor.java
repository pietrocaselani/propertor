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
import static propertor.Visibility.NONE;
import static propertor.Visibility.PRIVATE;
import static propertor.Visibility.PROTECTED;
import static propertor.Visibility.PUBLIC;

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

		if (containsMethod(classTree, methodName) || propertyModel.getPropertyAnnotation().setter() == NONE) return;

		final long setterFlags = getMethodFlags(propertyModel.getPropertyAnnotation().setter());

		final JCModifiers modifiers = mTreeMaker.Modifiers(setterFlags);

		final JCTree fieldType = propertyModel.getFieldDecl().getType();

		final JCExpression parameterType = wrapPrimitive(fieldType);

		final JCExpression returnExpression = mTreeMaker.TypeIdent(VOID);

		final JCVariableDecl paramDecl = mTreeMaker.VarDef(
				mTreeMaker.Modifiers(Flags.PARAMETER),
				getName(propertyModel.getPropertyName().substring(0, 1).toLowerCase() + propertyModel.getPropertyName().substring(1)),
				parameterType,
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

		if (containsMethod(classTree, methodName) || propertyModel.getPropertyAnnotation().getter() == NONE) return;

		final long getterFlags = getMethodFlags(propertyModel.getPropertyAnnotation().getter());

		final JCBlock getterBody = mTreeMaker.Block(0, List.<JCStatement>nil());

		final JCExpression expression = mTreeMaker.Ident(propertyModel.getFieldDecl());

		final JCReturn returnStmt = mTreeMaker.Return(expression);

		getterBody.stats = getterBody.stats.append(returnStmt);

		final JCModifiers modifiers = mTreeMaker.Modifiers(getterFlags);

		final JCTree fieldType = propertyModel.getFieldDecl().getType();

		final JCExpression returnType = wrapPrimitive(fieldType);

		final JCMethodDecl getter = mTreeMaker.MethodDef(modifiers, methodName, returnType, List.<JCTypeParameter>nil(),
				List.<JCVariableDecl>nil(), List.<JCExpression>nil(), getterBody, null);

		classTree.defs = classTree.defs.append(getter);
	}
	//endregion

	//region Helpers
	private JCExpression wrapPrimitive(JCTree tree) {
		return tree instanceof JCPrimitiveTypeTree ? mTreeMaker.TypeIdent(((JCPrimitiveTypeTree) tree).typetag) : (JCExpression) tree;
	}

	private long getMethodFlags(Visibility visibility) {
		final long flags;
		if (visibility == PUBLIC) {
			flags = Flags.PUBLIC;
		} else if (visibility == PROTECTED) {
			flags = Flags.PROTECTED;
		} else if (visibility == PRIVATE) {
			flags = Flags.PRIVATE;
		} else {
			flags = 0;
		}

		return flags;
	}

	private boolean containsMethod(JCClassDecl classDecl, Name methodName) {
		for (final JCTree def : classDecl.defs) {
			if (def instanceof JCMethodDecl) {
				JCMethodDecl methodDecl = (JCMethodDecl) def;
				if (methodDecl.name.equals(methodName)) {
					return true;
				}
			}
		}
		return false;
	}

	private ArrayList<PropertyModel> parsePropertyAnnotations(RoundEnvironment env) {
		final Set<? extends Element> propertyElements = env.getElementsAnnotatedWith(Property.class);

		ArrayList<PropertyModel> propertyModels = new ArrayList<PropertyModel>(propertyElements.size());

		for (final Element propertyElement : propertyElements) {
			propertyModels.add(new PropertyModel(propertyElement, processingEnv));
		}

		return propertyModels;
	}

	private Name getName(String s) {
		return fromString(Table.instance(((JavacProcessingEnvironment) processingEnv).getContext()), s);
	}
	//endregion
}