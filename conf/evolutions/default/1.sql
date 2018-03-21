# --- !Ups
create table translations(id bigint, phrase text, primary key(id));

# --- !Downs
drop table translations;