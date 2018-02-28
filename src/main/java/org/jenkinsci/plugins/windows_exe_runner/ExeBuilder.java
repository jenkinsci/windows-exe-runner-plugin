package org.jenkinsci.plugins.windows_exe_runner;

import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Plugin;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
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
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.jenkinsci.plugins.windows_exe_runner.util.StringUtil;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Yasuyuki Saito
 */
public class ExeBuilder extends Builder implements SimpleBuildStep {

    private final String exeName;
    private final String cmdLineArgs;
    private final boolean failBuild;

    /**
     *
     * @param exeName
     * @param cmdLineArgs
     * @param failBuild
     */
    @DataBoundConstructor
    public ExeBuilder(String exeName, String cmdLineArgs, boolean failBuild) {
        this.exeName = exeName;
        this.cmdLineArgs = cmdLineArgs;
        this.failBuild = failBuild;
    }

    public String getExeName() {
        return exeName;
    }

    public String getCmdLineArgs() {
        return cmdLineArgs;
    }

    public boolean isFailBuild() {
        return failBuild;
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
        EnvVars env = run.getEnvironment(tl);
        ExeInstallation installation = getInstallation();
        if (installation == null) {
            tl.fatalError("ExeInstallation not found.");
            //return false;
        }
        installation = installation.forNode(Computer.currentComputer().getNode(), tl);
        installation = installation.forEnvironment(env);

        // exe path.
        String exePath = getExePath(installation, launcher, tl);
        //if (StringUtil.isNullOrSpace(exePath)) return false;
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
        boolean r = exec(args, run, launcher, tl, env, workspace);
        //return r;
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
     * @param run
     * @param env
     * @param values
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
    private boolean exec(List<String> args, Run<?, ?> run, Launcher launcher, TaskListener tl, EnvVars env, FilePath workspace) throws InterruptedException, IOException {
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
                return (r == 0);
            } else {
                if (r != 0) {
                    run.setResult(Result.UNSTABLE);
                }
                return true;
            }
        } catch (IOException e) {
            Util.displayIOException(e, tl);
            e.printStackTrace(tl.fatalError("execution failed"));
            return false;
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
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @CopyOnWrite
        private volatile ExeInstallation[] installations = new ExeInstallation[0];

        DescriptorImpl() {
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
