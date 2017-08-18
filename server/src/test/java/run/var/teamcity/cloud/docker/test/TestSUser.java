package run.var.teamcity.cloud.docker.test;

import jetbrains.buildServer.groups.UserGroup;
import jetbrains.buildServer.notification.DuplicateNotificationRuleException;
import jetbrains.buildServer.notification.NotificationRule;
import jetbrains.buildServer.notification.NotificationRulesHolder;
import jetbrains.buildServer.notification.WatchedBuilds;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.Permissions;
import jetbrains.buildServer.serverSide.auth.Role;
import jetbrains.buildServer.serverSide.auth.RoleEntry;
import jetbrains.buildServer.serverSide.auth.RoleScope;
import jetbrains.buildServer.serverSide.auth.RolesHolder;
import jetbrains.buildServer.users.DuplicateUserAccountException;
import jetbrains.buildServer.users.EmptyUsernameException;
import jetbrains.buildServer.users.PropertyKey;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.users.UserNotFoundException;
import jetbrains.buildServer.users.VcsUsernamePropertyKey;
import jetbrains.buildServer.vcs.SVcsModification;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TestSUser implements SUser {

    private final Map<String, Set<Permission>> projectPermissions = new ConcurrentHashMap<>();

    @NotNull
    @Override
    public List<SVcsModification> getVcsModifications(int i) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @NotNull
    @Override
    public List<SVcsModification> getAllModifications() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public void updateUserAccount(@NotNull String s, String s1, String s2) throws UserNotFoundException,
            DuplicateUserAccountException, EmptyUsernameException {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public void setUserProperties(@NotNull Map<? extends PropertyKey, String> map) throws UserNotFoundException {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public void setUserProperty(@NotNull PropertyKey propertyKey, String s) throws UserNotFoundException {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public void deleteUserProperty(@NotNull PropertyKey propertyKey) throws UserNotFoundException {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public void setPassword(String s) throws UserNotFoundException {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @NotNull
    @Override
    public List<String> getProjectsOrder() throws UserNotFoundException {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public void setProjectsOrder(@NotNull List<String> list) throws UserNotFoundException {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public void setVisibleProjects(@NotNull Collection<String> collection) throws UserNotFoundException {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public void hideProject(@NotNull String s) throws UserNotFoundException {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public void setLastLoginTimestamp(@NotNull Date date) throws UserNotFoundException {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public void setBlockState(String s, String s1) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Nullable
    @Override
    public String getBlockState(String s) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @NotNull
    @Override
    public List<UserGroup> getUserGroups() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @NotNull
    @Override
    public List<UserGroup> getAllUserGroups() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @NotNull
    @Override
    public List<VcsUsernamePropertyKey> getVcsUsernameProperties() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @NotNull
    @Override
    public List<SBuildType> getOrderedBuildTypes(@Nullable SProject sProject) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @NotNull
    @Override
    public Collection<SBuildType> getBuildTypesOrder(@NotNull SProject sProject) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public void setBuildTypesOrder(@NotNull SProject sProject, @NotNull List<SBuildType> list, @NotNull
            List<SBuildType> list1) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public boolean isHighlightRelatedDataInUI() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @NotNull
    @Override
    public List<NotificationRule> getNotificationRules(@NotNull String s) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public void setNotificationRules(@NotNull String s, @NotNull List<NotificationRule> list) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public void removeRule(long l) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public void applyOrder(@NotNull String s, @NotNull long[] longs) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public long addNewRule(@NotNull String s, @NotNull NotificationRule notificationRule) throws
            DuplicateNotificationRuleException {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Nullable
    @Override
    public Collection<Long> findConflictingRules(@NotNull String s, @NotNull WatchedBuilds watchedBuilds) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Nullable
    @Override
    public NotificationRule findRuleById(long l) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @NotNull
    @Override
    public List<NotificationRulesHolder> getParentRulesHolders() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @NotNull
    @Override
    public List<NotificationRulesHolder> getAllParentRulesHolders() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @NotNull
    @Override
    public Collection<Role> getRolesWithScope(@NotNull RoleScope roleScope) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public Collection<RoleScope> getScopes() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @NotNull
    @Override
    public Collection<RoleEntry> getRoles() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public void addRole(@NotNull RoleScope roleScope, @NotNull Role role) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public void removeRole(@NotNull RoleScope roleScope, @NotNull Role role) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public void removeRole(@NotNull Role role) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public void removeRoles(@NotNull RoleScope roleScope) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public boolean isSystemAdministratorRoleGranted() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public boolean isSystemAdministratorRoleGrantedDirectly() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public boolean isSystemAdministratorRoleInherited() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @NotNull
    @Override
    public Collection<RolesHolder> getParentHolders() {
        return null;
    }

    @NotNull
    @Override
    public Collection<RolesHolder> getAllParentHolders() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public long getId() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public String getRealm() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public String getUsername() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public String getEmail() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public String getDescriptiveName() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public String getExtendedName() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public Date getLastLoginTimestamp() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public List<String> getVisibleProjects() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public List<String> getAllProjects() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @NotNull
    @Override
    public String describe(boolean b) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public boolean isPermissionGrantedGlobally(@NotNull Permission permission) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @NotNull
    @Override
    public Permissions getGlobalPermissions() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @NotNull
    @Override
    public Map<String, Permissions> getProjectsPermissions() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public boolean isPermissionGrantedForProject(@NotNull String s, @NotNull Permission permission) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public boolean isPermissionGrantedForAllProjects(@NotNull Collection<String> collection, @NotNull Permission
            permission) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public boolean isPermissionGrantedForAnyProject(@NotNull Permission permission) {
        return projectPermissions.values().stream().anyMatch(permissions -> permissions.contains(permission));
    }

    @NotNull
    @Override
    public Permissions getPermissionsGrantedForProject(@NotNull String s) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @NotNull
    @Override
    public Permissions getPermissionsGrantedForAllProjects(@NotNull Collection<String> collection) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Nullable
    @Override
    public User getAssociatedUser() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Nullable
    @Override
    public String getPropertyValue(PropertyKey propertyKey) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @Override
    public boolean getBooleanProperty(PropertyKey propertyKey) {
        throw new UnsupportedOperationException("Not a real user.");
    }

    @NotNull
    @Override
    public Map<PropertyKey, String> getProperties() {
        throw new UnsupportedOperationException("Not a real user.");
    }

    public TestSUser addProjectPermission(String projectId, Permission permission) {
        Set<Permission> permissions = projectPermissions.get(projectId);
        ;

        if (permissions == null) {
            permissions = EnumSet.noneOf(Permission.class);
        } else {
            permissions = EnumSet.copyOf(permissions);
        }

        permissions.add(permission);

        projectPermissions.put(projectId, permissions);
        return this;
    }
}
