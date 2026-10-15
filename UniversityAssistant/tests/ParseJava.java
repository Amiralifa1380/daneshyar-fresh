import java.io.File;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;
/** Syntax parse only. This does NOT replace compilation against android.jar. */
class ParseJava {
 public static void main(String[] args) throws Exception {
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
  DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
  try(StandardJavaFileManager files=compiler.getStandardFileManager(diagnostics,null,null)) {
   JavacTask task=(JavacTask)compiler.getTask(null,files,diagnostics,List.of("-proc:none"),null,files.getJavaFileObjects(args));
   task.parse();long errors=diagnostics.getDiagnostics().stream().filter(d->d.getKind()==Diagnostic.Kind.ERROR).count();
   diagnostics.getDiagnostics().forEach(System.out::println);
   if(errors!=0)throw new AssertionError("Java parse errors: "+errors);
   System.out.println("PASS: Java syntax parsed for "+args.length+" source files (no Android type checking)");
  }
 }
}
