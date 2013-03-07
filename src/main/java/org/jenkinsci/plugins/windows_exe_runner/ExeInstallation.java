package org.jenkinsci.plugins.windows_exe_runner;

import java.io.IOException;

import org.jenkinsci.plugins.windows_exe_runner.Messages;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentSpecific;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;

/**
* @author Yasuyuki Saito
*/
public final class ExeInstallation extends ToolInstallation implements NodeSpecific<ExeInstallation>, EnvironmentSpecific<ExeInstallation> {

    /** */
    private transient String pathToExe;

    @DataBoundConstructor
    public ExeInstallation(String name, String home) {
        super(name, home, null);
    }

    public ExeInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new ExeInstallation(getName(), translateFor(node, log));
    }

    public ExeInstallation forEnvironment(EnvVars environment) {
        return new ExeInstallation(getName(), environment.expand(getHome()));
    }

    protected Object readResolve() {
        if (this.pathToExe != null) {
            return new ExeInstallation(this.getName(), this.pathToExe);
        }
        return this;
    }

    /**
     * @author Yasuyuki Saito
     */
    @Extension
    public static class DescriptorImpl extends ToolDescriptor<ExeInstallation> {

        public String getDisplayName() {
            return Messages.ExeInstallation_DisplayName();
        }

        @Override
        public ExeInstallation[] getInstallations() {
            return Hudson.getInstance().getDescriptorByType(ExeBuilder.DescriptorImpl.class).getInstallations();
        }

        @Override
        public void setInstallations(ExeInstallation... installations) {
            Hudson.getInstance().getDescriptorByType(ExeBuilder.DescriptorImpl.class).setInstallations(installations);
        }

    }
}
