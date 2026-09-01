' Starts the BoodschapGemak API without a console window on screen.
'
' Scheduled to run at logon. All this does is launch run-server.cmd hidden;
' that script is what actually keeps the server alive and writes server.log.
' Paths are derived from this file's own location, so moving the repo does
' not break it - only the scheduled task's path would need updating.

Set fso = CreateObject("Scripting.FileSystemObject")
scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)

Set sh = CreateObject("WScript.Shell")
sh.Run """" & scriptDir & "\run-server.cmd""", 0, False
