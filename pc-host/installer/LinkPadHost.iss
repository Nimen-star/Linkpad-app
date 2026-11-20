; LinkPad PC Host Single-File Windows Installer (Inno Setup 6)
; Produces LinkPadHostSetup.exe with GUI progress wizard, shortcuts, and uninstaller.

#define MyAppName "LinkPad PC Host"
#define MyAppVersion "1.3.0"
#define MyAppPublisher "LinkPad"
#define MyAppURL "https://github.com/Nimen-star/linkpad-app"
#define MyAppExeName "LinkPadHost.exe"

#ifndef BuildOutputDir
  #define BuildOutputDir "..\bin\Release\net48"
#endif

[Setup]
AppId={{8E9B7D10-4A3F-4F8C-9F3C-2E7B6D4109A2}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}/releases
AppUpdatesURL={#MyAppURL}/releases/latest
DefaultDirName={autopf}\LinkPad
DefaultGroupName=LinkPad
DisableProgramGroupPage=yes
OutputDir=..\dist
OutputBaseFilename=LinkPadHostSetup
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
UninstallDisplayIcon={app}\{#MyAppExeName}
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#BuildOutputDir}\LinkPadHost.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#BuildOutputDir}\Nefarius.ViGEm.Client.dll"; DestDir: "{app}"; Flags: ignoreversion skipifsourcedoesntexist

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
