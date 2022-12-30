package org.jenkinsci.plugins.windows_exe_runner;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

/**
* @author Yasuyuki Saito
*/
public final class ExeInstallation extends ToolInstallation implements NodeSpecific<ExeInstallation>, EnvironmentSpecific<ExeInstallation> {

    /** */
    private transient String pathToExe;

    private final String defaultArgs;

    @DataBoundConstructor
    public ExeInstallation(String name, String home, String defaultArgs) {
        super(name, home, null);
        this.defaultArgs = Util.fixEmptyAndTrim(defaultArgs);
    }

    @Override
    public ExeInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new ExeInstallation(getName(), translateFor(node, log), this.defaultArgs);
    }

    @Override
    public ExeInstallation forEnvironment(EnvVars environment) {
        return new ExeInstallation(getName(), environment.expand(getHome()), this.defaultArgs);
    }

    @Override
    protected Object readResolve() {
        if (this.pathToExe != null) {
            return new ExeInstallation(this.getName(), this.pathToExe, this.defaultArgs);
        }
        return this;
    }

    public String getDefaultArgs() {
        return this.defaultArgs;
    }

    /**
     * @author ndeloof via git plugin
     */
    public static Node workspaceToNode(FilePath workspace) {
        Jenkins j = Jenkins.get();
        if (workspace != null && workspace.isRemote()) {
            for (Computer c : j.getComputers()) {
                if (c.getChannel() == workspace.getChannel()) {
                    Node n = c.getNode();
                    if (n != null) {
                        return n;
                    }
                }
            }
        }
        return j;
    }
    
    /**
     * @author Yasuyuki Saito
     */
    @Extension
    public static class DescriptorImpl extends ToolDescriptor<ExeInstallation> {

        @Override
        public String getDisplayName() {
            return Messages.ExeInstallation_DisplayName();
        }

        @Override
        public ExeInstallation[] getInstallations() {
            return Jenkins.get().getDescriptorByType(ExeBuilder.DescriptorImpl.class).getInstallations();
        }

        @Override
        public void setInstallations(ExeInstallation... installations) {
            Jenkins.get().getDescriptorByType(ExeBuilder.DescriptorImpl.class).setInstallations(installations);
        }

    }
}
