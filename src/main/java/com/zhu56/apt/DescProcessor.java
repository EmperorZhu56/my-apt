package com.zhu56.apt;

import com.google.auto.service.AutoService;
import com.history.core.inter.Optionable;
import com.history.core.util.StringUtil;
import com.history.core.util.stream.Ztream;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.ListBuffer;
import lombok.SneakyThrows;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.Objects;
import java.util.Set;

import static com.zhu56.apt.Constants.GET;


/**
 * desc处理器
 *
 * @author Zhu56
 * @since 2023/05/01
 */
@SupportedAnnotations(Desc.class)
@AutoService(Processor.class)
public class DescProcessor extends BaseProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        //首先获取被DescGetter注解标记的元素
        var set = roundEnv.getElementsAnnotatedWith(Desc.class);
        Ztream.of(set).forEach(e -> {
            //获取当前元素的JCTree对象
            JCTree jcTree = trees.getTree(e);
            jcTree.accept(new TreeTranslator() {
                @Override
                @SneakyThrows
                public void visitVarDef(JCTree.JCVariableDecl jcField) {
                    var jcClazz = getClassDeclForVariable(jcField);
                    if (Objects.isNull(jcClazz)) {
                        return;
                    }
                    // 获取 variableDecl 的类型
                    TypeMirror typeMirror = jcField.getType().type;
                    if (!isAssignable(typeMirror, Optionable.class)) {
                        warn("{0}#{1}不是{2}的子类，@{3}注解无效", jcClazz.sym.getQualifiedName(), jcField.name, Optionable.class.getName(), Desc.class.getSimpleName());
                        return;
                    }
                    addMethodDescMethod(jcClazz, jcField);
                }
            });
        });
        return true;
    }

    private void addMethodDescMethod(JCTree.JCClassDecl jcClazz, JCTree.JCVariableDecl jcField) {
        JCTree.JCFieldAccess thisField = Select(names._this, jcField.getName());
        JCTree.JCExpression ifExpr = NE(thisField, nullValue());
        // 创建if 内逻辑块
        JCTree.JCBlock ifBlock = Block(Return(Apply(Select(thisField, "getDesc"))));

        JCTree.JCMethodDecl jcMethod = buildGetMethod(
                GET + StringUtil.firstToUpper(jcField.getName().toString()) + "Desc",
                String.class,
                Block(If(ifExpr, ifBlock, returnNull()))
        );
        addMethod(jcClazz, jcMethod);
    }

    private void addImportIfNeeded(JCTree.JCClassDecl classDecl, Class<?> clazz) {
        boolean alreadyImported = Ztream.of(classDecl.getMembers())
                .anyMatch(member -> member instanceof JCTree.JCImport
                        && ((JCTree.JCImport) member).qualid != null
                        && ((JCTree.JCImport) member).qualid.toString().equals(clazz.getName()));
        if (alreadyImported) {
            return;
        }
        // 没有引入，则添加import语句
        JCTree.JCFieldAccess importIdent = Select(
                Ident(names.fromString(clazz.getPackage().getName())),
                names.fromString(clazz.getSimpleName())
        );
        JCTree.JCImport importDecl = Import(importIdent, false);
        classDecl.defs = classDecl.defs.appendList(ListBuffer.of(importDecl));
    }
}
