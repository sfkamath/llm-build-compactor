Why is this reporting 114 runs instead of 3




❯ mvn failsafe:integration-test -Dit.test=OllamaSummarizationExecutorIT -DllmCompactor.compressStackFrames=false
{
"status" : "SUCCESS",
"testsRun" : 114,
"failures" : 0,
"errors" : [ ],
"fixTargets" : [ ],
"recentChanges" : [ ]
}%                                                                                                                                                                                                                                                                                                                                                                                                            
❯ mvn failsafe:integration-test -Dit.test=OllamaSummarizationExecutorIT -DllmCompactor.enabled=false
[INFO] Scanning for projects...
[INFO]
[INFO] ----------------------< com.sfk:tui-sessions-mcp >----------------------
[INFO] Building tui-sessions-mcp 1.0-SNAPSHOT
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- failsafe:3.5.5:integration-test (default-cli) @ tui-sessions-mcp ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO]
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running core.summarization.OllamaSummarizationExecutorIT
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.912 s -- in core.summarization.OllamaSummarizationExecutorIT
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  4.477 s
[INFO] Finished at: 2026-03-07T17:01:52Z
[INFO] ------------------------------------------------------------------------
❯ mvn failsafe:integration-test -Dit.test=OllamaSummarizationExecutorIT
{
"status" : "SUCCESS",
"testsRun" : 114,
"failures" : 0,
"errors" : [ ],
"fixTargets" : [ ],
"recentChanges" : [ ]
}%         