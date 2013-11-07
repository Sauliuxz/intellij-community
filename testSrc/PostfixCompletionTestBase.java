import com.intellij.testFramework.fixtures.*;
import org.jetbrains.annotations.*;

public class PostfixCompletionTestBase extends LightCodeInsightFixtureTestCase {
  @Override protected String getTestDataPath() {
    return PostfixTestUtils.BASE_TEST_DATA_PATH + "/completion";
  }

  private void test(@NotNull final String typingChars) {
    final StackTraceElement[] trace = Thread.currentThread().getStackTrace();
    final String name = trace[2].getMethodName();

    myFixture.testCompletionTyping(name + ".java", typingChars, name + ".gold");
  }

  public void testIf01() { test("if\n"); }
  public void testIf02() { test("f\n"); }
  public void testIf03() { test("if\n"); }
}

