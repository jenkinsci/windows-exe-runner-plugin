package org.jenkinsci.plugins.windows_exe_runner;

import hudson.AbortException;
import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Plugin;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tools.ToolInstallation;
import hudson.util.ArgumentListBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.jenkinsci.plugins.windows_exe_runner.util.StringUtil;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * @author Yasuyuki Saito
 */
public class ExeBuilder extends Builder implements SimpleBuildStep {

    private final String exeName;
    @CheckForNull
    private String cmdLineArgs;
    private boolean failBuild = DescriptorImpl.DEFAULTFAILBUILD;

    /**
     *
     * @param exeName
     * @param cmdLineArgs
     * @param failBuild
     */
    
    @Deprecated
    public ExeBuilder(String exeName, String cmdLineArgs, boolean failBuild) {
        this.exeName = exeName;
        this.cmdLineArgs = cmdLineArgs;
        this.failBuild = failBuild;
    }
    
    @DataBoundConstructor
    public ExeBuilder(String exeName) {
        this.exeName = exeName;
    }

    public String getExeName() {
        return exeName;
    }

    @CheckForNull
    public String getCmdLineArgs() {
        return cmdLineArgs;
    }
    
    @DataBoundSetter
    public void setCmdLineArgs(String args){
        this.cmdLineArgs=Util.fixEmptyAndTrim(args);
    }

    public boolean getFailBuild() {
        return failBuild;
    }
    
    @DataBoundSetter
    public void setFailBuild(boolean f) {
        this.failBuild=f;
    }

    public ExeInstallation getInstallation() {
        if (exeName == null) {
            return null;
        }
        for (ExeInstallation i : DESCRIPTOR.getInstallations()) {
            if (exeName.equals(i.getName())) {
                return i;
            }
        }
        return null;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener tl) throws InterruptedException, IOException {
        //public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        ArrayList<String> args = new ArrayList<String>();
        EnvVars env = null;
        ExeInstallation installation = getInstallation();
        if (installation == null) {
            throw new AbortException("ExeInstallation not found.");
        }
        installation = installation.forNode(ExeInstallation.workspaceToNode(workspace), tl);
        
        if (run instanceof AbstractBuild) {
            env = run.getEnvironment(tl);
            installation = installation.forEnvironment(env);
        }

        // exe path.
        String exePath = getExePath(installation, launcher, tl);
        if (StringUtil.isNullOrSpace(exePath)){
            throw new AbortException("Exe path is blank.");
        }
        args.add(exePath);

        // Default Arguments
        if (!StringUtil.isNullOrSpace(installation.getDefaultArgs())) {
            args.addAll(getArguments(run, workspace, tl, installation.getDefaultArgs()));
        }

        // Manual Command Line String
        if (!StringUtil.isNullOrSpace(cmdLineArgs)) {
            args.addAll(getArguments(run, workspace, tl, cmdLineArgs));
        }

        // exe run.
        exec(args, run, launcher, tl, env, workspace);
    }

    /**
     *
     * @param installation
     * @param launcher
     * @param tl
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private String getExePath(ExeInstallation installation, Launcher launcher, TaskListener tl) throws InterruptedException, IOException {
        String pathToExe = installation.getHome();
        FilePath exec = new FilePath(launcher.getChannel(), pathToExe);

        try {
            if (!exec.exists()) {
                tl.fatalError(pathToExe + " doesn't exist");
                return null;
            }
        } catch (IOException e) {
            tl.fatalError("Failed checking for existence of " + pathToExe);
            return null;
        }

        tl.getLogger().println("Path To exe: " + pathToExe);
        return StringUtil.appendQuote(pathToExe);
    }

    /**
     *
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private List<String> getArguments(Run<?, ?> run, hudson.FilePath workspace, TaskListener tl, String values) throws InterruptedException, IOException {
        ArrayList<String> args = new ArrayList<String>();
        StringTokenizer valuesToknzr = new StringTokenizer(values, " \t\r\n");

        while (valuesToknzr.hasMoreTokens()) {
            String value = valuesToknzr.nextToken();
            if (run instanceof AbstractBuild) {
                Plugin p = Jenkins.getInstance().getPlugin("token-macro");
                if (null != p && p.getWrapper().isActive()) {
                    try {
                        value = TokenMacro.expandAll(run, workspace, tl, value);
                    } catch (MacroEvaluationException ex) {
                        tl.error("TokenMacro was unable to evaluate: " + value + " " + ex.getMessage());
                    }
                } else {
                    EnvVars envVars = run.getEnvironment(tl);
                    value = envVars.expand(value);
                }
            }
            if (!StringUtil.isNullOrSpace(value)) {
                args.add(value);
            }
        }
        return args;
    }

    /**
     *
     * @param args
     * @param build
     * @param launcher
     * @param tl
     * @param env
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private void exec(List<String> args, Run<?, ?> run, Launcher launcher, TaskListener tl, EnvVars env, FilePath workspace) throws InterruptedException, IOException {
        ArgumentListBuilder cmdExecArgs = new ArgumentListBuilder();
        FilePath tmpDir = null;
        //FilePath pwd = run.getWorkspace();

        if (!launcher.isUnix()) {
            tmpDir = workspace.createTextTempFile("exe_runner_", ".bat", StringUtil.concatString(args), false);
            cmdExecArgs.add("cmd.exe", "/C", tmpDir.getRemote(), "&&", "exit", "%ERRORLEVEL%");
        } else {
            for (String arg : args) {
                cmdExecArgs.add(arg);
            }
        }

        tl.getLogger().println("Executing : " + cmdExecArgs.toStringWithQuote());

        try {
            int r = launcher.launch().cmds(cmdExecArgs).envs(env).stdout(tl).pwd(workspace).join();

            if (failBuild) {
                if (r!=0){
                    throw new AbortException("Exited with code: " + r);
                }
            } else {
                if (r != 0) {
                    tl.getLogger().println("Exe exited with code: " + r);
                    run.setResult(Result.UNSTABLE);
                }
            }
        } catch (IOException e) {
            Util.displayIOException(e, tl);
            e.printStackTrace(tl.fatalError("execution failed"));
            throw new AbortException("execution failed");
        } finally {
            try {
                if (tmpDir != null) {
                    tmpDir.delete();
                }
            } catch (IOException e) {
                Util.displayIOException(e, tl);
                e.printStackTrace(tl.fatalError("temporary file delete failed"));
            }
        }
    }

    @Override
    public Descriptor<Builder> getDescriptor() {
        return DESCRIPTOR;
    }

    /**
     * Descriptor should be singleton.
     */
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    /**
     * @author Yasuyuki Saito
     */
    @Symbol ("runexe")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public static final boolean DEFAULTFAILBUILD =true;
        @CopyOnWrite
        private volatile ExeInstallation[] installations = new ExeInstallation[0];

        public DescriptorImpl() {
            super(ExeBuilder.class);
            load();
        }

        @Override
        public String getDisplayName() {
            return Messages.ExeBuilder_DisplayName();
        }

        public ExeInstallation[] getInstallations() {
            return installations;
        }

        public void setInstallations(ExeInstallation... installations) {
            this.installations = installations;
            save();
        }

        /**
         * Obtains the {@link ExeInstallation.DescriptorImpl} instance.
         */
        public ExeInstallation.DescriptorImpl getToolDescriptor() {
            return ToolInstallation.all().get(ExeInstallation.DescriptorImpl.class);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
