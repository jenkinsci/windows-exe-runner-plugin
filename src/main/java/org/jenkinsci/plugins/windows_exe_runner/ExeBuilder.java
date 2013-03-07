package org.jenkinsci.plugins.windows_exe_runner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.jenkinsci.plugins.windows_exe_runner.util.StringUtil;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.tasks.Builder;
import hudson.tools.ToolInstallation;
import hudson.util.ArgumentListBuilder;

/**
 * @author Yasuyuki Saito
 */
public class ExeBuilder extends Builder {

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
        this.exeName     = exeName;
        this.cmdLineArgs = cmdLineArgs;
        this.failBuild   = failBuild;
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
        if (exeName == null) return null;
        for (ExeInstallation i : DESCRIPTOR.getInstallations()) {
            if (exeName.equals(i.getName()))
                return i;
        }
        return null;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        ArrayList<String> args = new ArrayList<String>();
        EnvVars env = build.getEnvironment(listener);

        // exe path.
        String exePath = getExePath(launcher, listener, env);
        if (StringUtil.isNullOrSpace(exePath)) return false;
        args.add(exePath);

        // Manual Command Line String
        if (!StringUtil.isNullOrSpace(cmdLineArgs))
            args.addAll(getArguments(build, env, cmdLineArgs));

        // exe run.
        boolean r = exec(args, build, launcher, listener, env);

        return r;
    }


    /**
     *
     * @param  launcher
     * @param  listener
     * @param  env
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private String getExePath(Launcher launcher, BuildListener listener, EnvVars env) throws InterruptedException, IOException {
        ExeInstallation installation = getInstallation();

        if (installation == null) {
            return null;
        } else {
            installation = installation.forNode(Computer.currentComputer().getNode(), listener);
            installation = installation.forEnvironment(env);
            String pathToExe = installation.getHome();
            FilePath exec = new FilePath(launcher.getChannel(), pathToExe);

            try {
                if (!exec.exists()) {
                    listener.fatalError(pathToExe + " doesn't exist");
                    return null;
                }
            } catch (IOException e) {
                listener.fatalError("Failed checking for existence of " + pathToExe);
                return null;
            }

            listener.getLogger().println("Path To exe: " + pathToExe);
            return StringUtil.appendQuote(pathToExe);
        }
    }

    /**
     *
     * @param  build
     * @param  env
     * @param  values
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private List<String> getArguments(AbstractBuild<?, ?> build, EnvVars env, String values) throws InterruptedException, IOException {
        ArrayList<String> args = new ArrayList<String>();
        StringTokenizer valuesToknzr = new StringTokenizer(values, " \t\r\n");

        while (valuesToknzr.hasMoreTokens()) {
            String value = valuesToknzr.nextToken();
            value = Util.replaceMacro(value, env);
            value = Util.replaceMacro(value, build.getBuildVariables());

            if (!StringUtil.isNullOrSpace(value))
                args.add(value);
        }

        return args;
    }

    /**
     *
     * @param  args
     * @param  build
     * @param  launcher
     * @param  listener
     * @param  env
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private boolean exec(List<String> args, AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, EnvVars env) throws InterruptedException, IOException {
        ArgumentListBuilder cmdExecArgs = new ArgumentListBuilder();
        FilePath tmpDir = null;
        FilePath pwd = build.getWorkspace();

        if (!launcher.isUnix()) {
            tmpDir = pwd.createTextTempFile("exe_runner_", ".bat", StringUtil.concatString(args), false);
            cmdExecArgs.add("cmd.exe", "/C", tmpDir.getRemote(), "&&", "exit", "%ERRORLEVEL%");
        } else {
            for (String arg : args) {
                cmdExecArgs.add(arg);
            }
        }

        listener.getLogger().println("Executing : " + cmdExecArgs.toStringWithQuote());

        try {
            int r = launcher.launch().cmds(cmdExecArgs).envs(env).stdout(listener).pwd(pwd).join();

            if (failBuild)
                return (r == 0);
            else {
                if (r != 0)
                    build.setResult(Result.UNSTABLE);
                return true;
            }
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError("execution failed"));
            return false;
        } finally {
            try {
                if (tmpDir != null) tmpDir.delete();
            } catch (IOException e) {
                Util.displayIOException(e, listener);
                e.printStackTrace(listener.fatalError("temporary file delete failed"));
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
    public static final class DescriptorImpl extends Descriptor<Builder> {

        @CopyOnWrite
        private volatile ExeInstallation[] installations = new ExeInstallation[0];

        DescriptorImpl() {
            super(ExeBuilder.class);
            load();
        }

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
    }
}
