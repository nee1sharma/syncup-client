# LazySyncUp — Product Requirements

Implementation planning is split into the [Android client design](android-client-design.md), and [shared system design](backup-system-design.md).

The system will back up photos, videos, and files to a laptop.
Features: 

  1. There is an app to backup them laptop. by simply clicking backup button. There should option to backup from which location. Which file types, dates (today, yesterday, date between etc) to be backed up. We can set default configurations.
  2. I should be able to pull data from server as well.
  3. App will work in private network like wifi.
  4. Devices connected to private network will be able to back their files.
  5. Since videos are large size, Fastest data transfer is expected.

For now I want to develop android app which will do above.
Probably we need a server as well. It could be simple console. Let say if server is started clients can automatically connect to it. For consider we have single server later we will plan select in which server user want to connect and backup.
I am familiar with Java programming language. 
