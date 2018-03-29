# --- !Ups
create sequence phrases_id_seq;
create table phrases(
  id bigint not null default nextval('phrases_id_seq'),
  text text, primary key(id)
);

# --- !Downs
drop table phrases;
drop sequence phrases_id_seq;